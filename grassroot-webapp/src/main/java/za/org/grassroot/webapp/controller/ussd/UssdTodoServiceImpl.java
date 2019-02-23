package za.org.grassroot.webapp.controller.ussd;

import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.domain.task.TodoRequest;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.TodoDeadlineNotInFutureException;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.TodoRequestBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static za.org.grassroot.core.domain.task.TodoType.*;
import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;


@Service
public class UssdTodoServiceImpl implements UssdTodoService {
	private static final int PAGE_SIZE = 3;

	private final Logger log = LoggerFactory.getLogger(UssdTodoServiceImpl.class);

	private final UssdSupport ussdSupport;
	private final USSDMessageAssembler messageAssembler;
	private final UserManagementService userManager;
	private final TodoBroker todoBroker;
	private final TodoRequestBroker todoRequestBroker;
	private final CacheUtilService cacheManager;
	private final USSDGroupUtil groupUtil;
	private final AccountFeaturesBroker accountFeaturesBroker;
	private final LearningService learningService;
	private final MemberDataExportBroker dataExportBroker;

	public UssdTodoServiceImpl(UssdSupport ussdSupport, USSDMessageAssembler messageAssembler, UserManagementService userManager, TodoBroker todoBroker, TodoRequestBroker todoRequestBroker, CacheUtilService cacheManager, USSDGroupUtil groupUtil, AccountFeaturesBroker accountFeaturesBroker, LearningService learningService, MemberDataExportBroker dataExportBroker) {
		this.ussdSupport = ussdSupport;
		this.messageAssembler = messageAssembler;
		this.userManager = userManager;
		this.todoBroker = todoBroker;
		this.todoRequestBroker = todoRequestBroker;
		this.cacheManager = cacheManager;
		this.groupUtil = groupUtil;
		this.accountFeaturesBroker = accountFeaturesBroker;
		this.learningService = learningService;
		this.dataExportBroker = dataExportBroker;
	}

	@Override
	public USSDMenu respondToTodo(User user, Todo todo) {
		log.info("Generating response menu for entity: {}", todo);
		switch (todo.getType()) {
			case INFORMATION_REQUIRED:
				final String infoPrompt = messageAssembler.getMessage("todo.info.prompt",
						new String[]{todo.getCreatorAlias(), todo.getMessage()}, user);
				return new USSDMenu(infoPrompt, REL_PATH + "/respond/info?todoUid=" + todo.getUid());
			case VOLUNTEERS_NEEDED:
				final String volunteerPrompt = messageAssembler.getMessage("todo.volunteer.prompt",
						new String[]{todo.getCreatorAlias(), todo.getMessage()}, user);
				log.info("volunteer todo assembled, prompt: {}", volunteerPrompt);
				return new USSDMenu(volunteerPrompt, ussdSupport.optionsYesNo(user, "todo/respond/volunteer?todoUid=" + todo.getUid()));
			case VALIDATION_REQUIRED:
				final String confirmationPrompt = messageAssembler.getMessage("todo.validate.prompt",
						new String[]{todo.getCreatorAlias(), todo.getMessage()}, user);
				USSDMenu menu = new USSDMenu(confirmationPrompt);
				menu.addMenuOptions(ussdSupport.optionsYesNo(user, "todo/respond/validate?todoUid=" + todo.getUid()));
				menu.addMenuOption(REL_PATH + "/respond/validate?todoUid=" + todo.getUid() + "&" + UssdSupport.yesOrNoParam + "=unsure",
						messageAssembler.getMessage("todo.validate.option.unsure", user));
				return menu;
			default:
				throw new TodoTypeMismatchException();
		}
	}

	@Override
	@Transactional
	public Request processVolunteerResponse(String msisdn, String todoUid, String userResponse) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		todoBroker.recordResponse(user.getUid(), todoUid, userResponse, false);
		// todo : if user responds yes, allow them to share? in case yes, leaving a little duplication in here
		String promptMessage = "yes".equalsIgnoreCase(userResponse) ?
				messageAssembler.getMessage("todo.volunteer.yes.prompt", user) :
				messageAssembler.getMessage("todo.volunteer.no.prompt", user);
		log.info("User={},Prompt={},todo uid={},response={}", user, promptMessage, todoUid, userResponse);
		return userManager.needToPromptForLanguage(user, ussdSupport.preLanguageSessions) ?
				ussdSupport.menuBuilder(ussdSupport.promptLanguageMenu(user)) :
				ussdSupport.menuBuilder(ussdSupport.welcomeMenu(promptMessage, user));
	}

	@Override
	@Transactional
	public Request processConfirmInfoResponse(String msisdn, String todoUid, String userResponse, String priorInput) throws URISyntaxException {
		final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
		User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info", todoUid, userInput));
		log.debug("User input={},User={}", userInput, user);
		if ("0".equals(userResponse)) {
			// so user doesn't get asked on infinite loop
			todoBroker.recordResponse(user.getUid(), todoUid, "SKIPPED", false);
			return ussdSupport.menuBuilder(ussdSupport.welcomeMenu(ussdSupport.getMessage("home.start.prompt", user), user));
		} else {
			UserMinimalProjection overload = new UserMinimalProjection(user.getUid(), user.getDisplayName(), user.getLanguageCode(), user.getProvince());
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage(USSDSection.TODO, "info", promptKey + ".confirm", userInput, overload));
			menu.addMenuOption(REL_PATH + "/respond/info/confirmed?todoUid=" + todoUid +
					"&response=" + USSDUrlUtil.encodeParameter(userInput), messageAssembler.getMessage("options.yes", user));
			menu.addMenuOption(REL_PATH + "/respond/info/revise", messageAssembler.getMessage("todo.info.response.change", user));
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processReviseInfoRequest(String msisdn, String todoUid, String userResponse, String priorInput) throws URISyntaxException {
		final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
		User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info/revise", todoUid, userInput));
		// note: probably want to come back & test whether to re-include original request
		final String prompt = messageAssembler.getMessage("todo.info.revise.prompt", new String[]{userInput}, user);
		log.info("prompt={},user={},user input={}", prompt, user, userInput);
		return ussdSupport.menuBuilder(new USSDMenu(prompt, REL_PATH + "/respond/info?todoUid=" + todoUid));
	}

	@Override
	@Transactional
	public Request processRecordInfoResponse(String msisdn, String todoUid, String response) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, null);
		todoBroker.recordResponse(user.getUid(), todoUid, response, false);
		return userManager.needToPromptForLanguage(user, ussdSupport.preLanguageSessions) ? ussdSupport.menuBuilder(ussdSupport.promptLanguageMenu(user)) :
				ussdSupport.menuBuilder(ussdSupport.welcomeMenu(messageAssembler.getMessage("todo.info.prompt.done", user), user));
	}

	private String saveUrl(String menu, String todoUid, String userInput) {
		return REL_PATH + menu + "?todoUid=" + todoUid + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
	}

	@Override
	@Transactional(readOnly = true)
	public Request processStart(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.start.prompt", user));
		menu.addMenuOption(REL_PATH + "/create?type=" + ACTION_REQUIRED,
				messageAssembler.getMessage("todo.start.options.action", user));
		menu.addMenuOption(REL_PATH + "/create?type=" + VOLUNTEERS_NEEDED,
				messageAssembler.getMessage("todo.start.options.volunteer", user));
		menu.addMenuOption(REL_PATH + "/create?type=" + INFORMATION_REQUIRED,
				messageAssembler.getMessage("todo.start.options.info", user));
		menu.addMenuOption(REL_PATH + "/existing", messageAssembler.getMessage("todo.start.options.existing", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processCreate(String msisdn, TodoType type, String storedUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		final String requestUid = StringUtils.isEmpty(storedUid) ? todoRequestBroker.create(user.getUid(), type).getUid() : storedUid;
		cacheManager.putUssdMenuForUser("/create", requestUid);
		USSDGroupUtil.GroupMenuBuilder groupMenu = new USSDGroupUtil.GroupMenuBuilder(user, USSDSection.TODO)
				.messageKey("group").urlForExistingGroup("create/subject?storedUid=" + requestUid);
		return ussdSupport.menuBuilder(groupUtil.askForGroup(groupMenu));
	}

	@Override
	@Transactional
	public Request processAskForSubject(String msisdn, String storedUid, String groupUid, Boolean revising) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/create/subject", storedUid, null));
		if (!StringUtils.isEmpty(groupUid)) {
			int todosLeft = accountFeaturesBroker.numberTodosLeftForGroup(groupUid);
			if (todosLeft < 0) {
				return ussdSupport.menuBuilder(outOfTodosMenu(user));
			}
			todoRequestBroker.updateGroup(user.getUid(), storedUid, groupUid);
		}
		TodoRequest todoRequest = todoRequestBroker.load(storedUid);
		final String prompt = messageAssembler.getMessage("todo.msg." + getKeyForSubject(todoRequest.getType()) + ".prompt", user);
		log.info("todo req={},user={},prompt={}", todoRequest, user, prompt);
		return ussdSupport.menuBuilder(new USSDMenu(prompt, revising == null || !revising
				? REL_PATH + "/create/deadline?storedUid=" + storedUid
				: REL_PATH + "/create/confirm?field=subject&storedUid=" + storedUid));
	}

	@Override
	@Transactional
	public Request processAskForDeadline(String msisdn, String storedUid, String userInput, String priorInput, Boolean revising) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/create/deadline", storedUid, priorInput));
		String subject = StringUtils.isEmpty(priorInput) ? userInput : priorInput;
		if (revising == null || !revising) {
			todoRequestBroker.updateMessage(user.getUid(), storedUid, subject.trim());
		}
		TodoRequest todoRequest = todoRequestBroker.load(storedUid);
		final String prompt = messageAssembler.getMessage("todo." + getKeyForSubject(todoRequest.getType()) + ".deadline", user);
		final String nextUrl = INFORMATION_REQUIRED.equals(todoRequest.getType()) && (revising == null || !revising) ?
				REL_PATH + "/create/tag?storedUid=" + storedUid :
				REL_PATH + "/create/confirm?storedUid=" + storedUid;
		return ussdSupport.menuBuilder(new USSDMenu(prompt, nextUrl));
	}

	@Override
	@Transactional
	public Request processAskForResponseTag(String msisdn, String storedUid, String userInput, String priorInput, Boolean revising) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/create/tag", storedUid, priorInput));
		if (revising == null || !revising) {
			final String dateTime = StringUtils.isEmpty(priorInput) ? userInput : priorInput;
			todoRequestBroker.updateDueDate(user.getUid(), storedUid, handleUserDateTimeInput(dateTime));
		}
		final String prompt = messageAssembler.getMessage("todo.info.tag.prompt", user);
		return ussdSupport.menuBuilder(new USSDMenu(prompt, REL_PATH + "/create/confirm?storedUid=" + storedUid + "&field=tag"));
	}

	@Override
	@Transactional
	public Request processValidateTodoCompletion(String msisdn, String todoUid, String userResponse) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		if (!"unsure".equalsIgnoreCase(userResponse)) {
			todoBroker.recordResponse(user.getUid(), todoUid, userResponse, false);
		}
		log.info("User={},response={}", user, userResponse);
		return ussdSupport.menuBuilder(ussdSupport.welcomeMenu(messageAssembler.getMessage("todo.validate." + userResponse + ".prompt", user), user));
	}

	@Override
	@Transactional
	public Request processConfirmTodoCreation(String msisdn, String storedUid, String userInput, String priorInput, String field) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/confirm", storedUid, priorInput)
				+ (field == null ? "" : "&field=" + field));

		final String value = StringUtils.isEmpty(priorInput) ? userInput : priorInput;
		log.info("confirm menu, field = {}, value = {}", field, value);
		if (StringUtils.isEmpty(field)) {
			todoRequestBroker.updateDueDate(user.getUid(), storedUid, handleUserDateTimeInput(value));
		} else {
			if ("tag".equals(field)) {
				todoRequestBroker.updateResponseTag(user.getUid(), storedUid, value.trim());
			} else if ("subject".equals(field)) {
				todoRequestBroker.updateMessage(user.getUid(), storedUid, value.trim());
			}
		}

		TodoRequest request = todoRequestBroker.load(storedUid);

		String formattedDueDate = UssdSupport.dateTimeFormat.format(convertToUserTimeZone(request.getActionByDate(), getSAST()));

		if (request.getActionByDate() != null && request.getActionByDate().isBefore(Instant.now())) {
			log.info("Action by date is in past, returning error");
			return ussdSupport.menuBuilder(errorMenuTimeInPast(user, request, formattedDueDate));
		} else {
			String[] promptFields = new String[]{request.getMessage(),
					request.getParent().getName(),
					formattedDueDate,
					INFORMATION_REQUIRED.equals(request.getType()) ? request.getResponseTag() : ""};
			final String promptKey = isWithinAnHour(request) ?
					"todo." + getKeyForSubject(request.getType()) + ".confirm.instant" :
					"todo." + getKeyForSubject(request.getType()) + ".confirm";
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage(promptKey, promptFields, user));
			menu.addMenuOption(REL_PATH + "/create/complete?storedUid=" + request.getUid(),
					messageAssembler.getMessage("options.yes", user));
			menu.addMenuOption(REL_PATH + "/create/subject?storedUid=" + request.getUid() + "&revising=true",
					messageAssembler.getMessage("todo.confirm.options.subject", user));
			menu.addMenuOption(REL_PATH + "/create/deadline?storedUid=" + request.getUid() + "&revising=true",
					messageAssembler.getMessage("todo.confirm.options.date", user));
			if (INFORMATION_REQUIRED.equals(request.getType())) {
				menu.addMenuOption(REL_PATH + "/create/tag?storedUid=" + request.getUid() + "&revising=true",
						messageAssembler.getMessage("todo.confirm.options.tag", user));
			}

			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processFinishTodoEntry(String inputNumber, String storedUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		todoRequestBroker.finish(storedUid);
		log.info("User={},storeuid={}", user, storedUid);
		return ussdSupport.menuBuilder(new USSDMenu(messageAssembler.getMessage("todo.done.prompt", user), ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional(readOnly = true)
	public Request processViewExistingTodos(String inputNumber, Integer page, Boolean fetchAll) throws URISyntaxException {
		int pageNumber = page == null ? 0 : page;
		boolean fetchAllTodos = fetchAll == null ? false : fetchAll;

		PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE, Sort.Direction.DESC, "createdDateTime");
		User user = userManager.findByInputNumber(inputNumber, REL_PATH + "/existing?page=" + pageNumber + "&fetchAll=" + fetchAllTodos);
		final String backUrl = determineBackMenu(pageNumber, fetchAllTodos);

		return ussdSupport.menuBuilder(!fetchAllTodos
				? listCreatedTodos(user, pageRequest, backUrl)
				: listAllUserTodos(user, pageRequest, pageNumber == 0, backUrl));
	}

	@Override
	@Transactional
	public Request processAlterDateConfirm(String msisdn, String todoUid, String userInput, String priorInput) throws URISyntaxException {
		final String enteredValue = priorInput == null ? userInput : priorInput;
		User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/date/confirm", todoUid, enteredValue));
		LocalDateTime parsedDateTime = handleUserDateTimeInput(enteredValue);
		log.info("ParsedDateTime={},User={}", parsedDateTime, user);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("changing this", new String[]{ussdSupport.dateFormat.format(parsedDateTime)}, user));
		final String confirmUrl = REL_PATH + "/modify/date/complete?todoUid=" + todoUid + "&epochMillis="
				+ DateTimeUtil.convertToSystemTime(parsedDateTime, DateTimeUtil.getSAST());
		menu.addMenuOption(confirmUrl, messageAssembler.getMessage("options.yes", user));
		menu.addMenuOption(REL_PATH + "/modify/date?todoUid=" + todoUid, messageAssembler.getMessage("options.no", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAlterTodoDate(String msisdn, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/date", todoUid, null));
		Todo todo = todoBroker.load(todoUid);
		USSDMenu menu = new USSDMenu(
				messageAssembler.getMessage("todo.modify.date.prompt", new String[]{ussdSupport.dateFormat.format(todo.getActionByDateAtSAST())}, user),
				REL_PATH + "/modify/date/confirm?todoUid=" + todoUid);
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAlterTodoMenu(String msisdn, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/", todoUid, null));
		Todo todo = todoBroker.load(todoUid);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.change.prompt", new String[]{todo.getName()}, user));
		final String keyRoot = "todo.change.options";
		menu.addMenuOption(REL_PATH + "/modify/subject?todoUid=" + todoUid,
				messageAssembler.getMessage(keyRoot + ".subject", user));
		menu.addMenuOption(REL_PATH + "/modify/date?todoUid=" + todoUid,
				messageAssembler.getMessage(keyRoot + ".duedate", user));
		menu.addMenuOption(REL_PATH + "/modify/cancel?todoUid=" + todoUid,
				messageAssembler.getMessage(keyRoot + ".cancel", user));
		menu.addMenuOption(REL_PATH + "/view?todoUid=" + todoUid,
				messageAssembler.getMessage(optionsKey + "back", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAlterTodoSubject(String msisdn, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/subject", todoUid, null));
		Todo todo = todoBroker.load(todoUid);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.modify.subject.prompt", new String[]{todo.getName()}, user),
				REL_PATH + "/modify/subject/done?todoUid=" + todoUid);
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processCancelTodo(String msisdn, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/cancel", todoUid, null));
		Todo todo = todoBroker.load(todoUid);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.cancel.prompt", new String[]{todo.getName()}, user));
		menu.addMenuOptions(ussdSupport.optionsYesNo(user, "todo/modify/cancel/confirm?todoUid=" + todoUid));
		log.info("Options={}", menu.getMenuOptions());
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processViewTodoEntry(String inputNumber, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Todo todo = todoBroker.load(todoUid);

		final String urlSuffix = "?todoUid=" + todo.getUid();
		List<TodoAssignment> todoAssignments = todoBroker.fetchAssignedUserResponses(user.getUid(), todoUid, true,
				false, false);

		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.view.prompt.desc", new String[]{
				todo.getMessage(),
				todo.getParent().getName(),
				shortDateFormat.format(todo.getDeadlineTimeAtSAST()),
				String.valueOf(todoAssignments.size())
		}, user));

		if (todo.getCreatedByUser().equals(user)) {
			menu.addMenuOption(REL_PATH + "/view/email" + urlSuffix, messageAssembler.getMessage("todo.view.options.email", user));
			menu.addMenuOption(REL_PATH + "/complete/prompt" + urlSuffix, messageAssembler.getMessage("todo.view.options.complete", user));
			menu.addMenuOption(REL_PATH + "/modify/" + urlSuffix, messageAssembler.getMessage("todo.view.options.modify", user));
		} else if (todoAssignments.stream().anyMatch(ta -> ta.getUser().equals(user))) {
			menu.addMenuOption(REL_PATH + "/respond" + urlSuffix,
					messageAssembler.getMessage("todo.view.options.response.change", user));
		} else if (todoBroker.canUserRespond(user.getUid(), todoUid)) {
			menu.addMenuOption(REL_PATH + "/respond" + urlSuffix,
					messageAssembler.getMessage("todo.view.options.response.enter", user));
		}

		menu.addMenuOption(REL_PATH + "/existing", messageAssembler.getMessage("options.back", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processEmailResponseDo(String inputNumber, String todoUid, String emailAddress) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		log.info("User={}", user);
		boolean isEmailValid = EmailValidator.getInstance().isValid(emailAddress);
		if (isEmailValid || user.hasEmailAddress() && emailAddress.length() == 1) {
			final String emailToPass = emailAddress.length() == 1 ? user.getEmailAddress() : emailAddress;
			dataExportBroker.emailTodoResponses(user.getUid(), todoUid, emailToPass);
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.email.prompt.done", user));
			return ussdSupport.menuBuilder(addBackHomeExit(menu, todoUid, user));
		} else {
			String prompt = messageAssembler.getMessage("todo.email.prompt.error", new String[]{emailAddress}, user);
			return ussdSupport.menuBuilder(new USSDMenu(prompt, REL_PATH + "/view/email/send?todoUid=" + todoUid));
		}
	}

	@Override
	@Transactional
	public Request processMarkTodoCompleteDone(String inputNumber, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		todoBroker.updateTodoCompleted(user.getUid(), todoUid, true);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.complete.done", user));
		return ussdSupport.menuBuilder(addBackHomeExit(menu, todoUid, user));
	}

	@Override
	@Transactional
	public Request processConfirmSubjectModification(String msisdn, String todoUid, String subject) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, null);
		if (!StringUtils.isEmpty(subject) && subject.length() > 0) {
			todoBroker.updateSubject(user.getUid(), todoUid, subject);
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.modified.done", user));
			return ussdSupport.menuBuilder(addBackHomeExit(menu, todoUid, user));
		} else {
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.subject.error", new String[]{subject}, user),
					REL_PATH + "/modify/subject/done?todoUid=" + todoUid);
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processChangeEntryConfirmed(String msisdn, String todoUid, long epochMillis) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, null);
		try {
			todoBroker.extend(user.getUid(), todoUid, Instant.ofEpochMilli(epochMillis));
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.modified.done", user));
			return ussdSupport.menuBuilder(addBackHomeExit(menu, todoUid, user));
		} catch (TodoDeadlineNotInFutureException e) {
			// todo : catch this during creation too
			ZonedDateTime zdt = DateTimeUtil.convertToUserTimeZone(Instant.ofEpochMilli(epochMillis), DateTimeUtil.getSAST());
			USSDMenu menu = new USSDMenu(
					messageAssembler.getMessage("todo.date.notfuture", new String[]{dateFormat.format(zdt)}, user),
					REL_PATH + "/modify/date/confirm?todoUid=" + todoUid);
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processCancelTodoConfirmed(String msisdn, String todoUid, boolean confirmed) throws URISyntaxException {
		if (confirmed) {
			User user = userManager.findByInputNumber(msisdn, null);
			todoBroker.cancel(user.getUid(), todoUid, false, null);
			USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.cancel.prompt", user));
			menu.addMenuOption(REL_PATH + "/start", messageAssembler.getMessage("todo.cancel.done.todos", user));
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, true));
			return ussdSupport.menuBuilder(menu);
		} else {
			return processViewTodoEntry(msisdn, todoUid);
		}
	}

	@Override
	@Transactional
	public Request processEmailTodoResponses(String inputNumber, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, REL_PATH + "/view/email?todoUid=" + todoUid);
		final String messagePrompt = user.hasEmailAddress() ?
				messageAssembler.getMessage("todo.email.prompt.existing", user) :
				messageAssembler.getMessage("todo.email.prompt.none", user);
		return ussdSupport.menuBuilder(new USSDMenu(messagePrompt, REL_PATH + "/view/email/send?todoUid=" + todoUid));
	}

	@Override
	@Transactional
	public Request processMarkTodoCompletePrompt(String inputNumber, String todoUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.complete.prompt", user));
		menu.addMenuOption(REL_PATH + "/complete/done?todoUid=" + todoUid, messageAssembler.getMessage("options.yes", user));
		menu.addMenuOption(REL_PATH + "/view?toodUid=" + todoUid, messageAssembler.getMessage("options.back", user));
		return ussdSupport.menuBuilder(menu);
	}

	private USSDMenu listAllUserTodos(User user, PageRequest pageRequest, boolean offerCreatedOnly, String backUrl) {
		Page<Todo> todosForUser = todoBroker.fetchPageOfTodosForUser(user.getUid(), false, pageRequest);
		log.info("User todos={}", todosForUser);
		if (!todosForUser.hasContent()) {
			return pageEmptyWithSwitchOption(pageRequest.getPageNumber(), false, backUrl, user);
		} else {
			USSDMenu menu = listTodos(messageAssembler.getMessage("todo.list.all.prompt", user), todosForUser, user);
			if (offerCreatedOnly) {
				menu.addMenuOption(REL_PATH + "/existing?fetchAll=false", messageAssembler.getMessage("todo.list.options.created", user));
			} else {
				menu.addMenuOption(REL_PATH + "/start", messageAssembler.getMessage("todo.list.options.createnew", user));
			}
			return menu;
		}
	}

	private USSDMenu listTodos(String prompt, Page<Todo> todos, final User user) {
		USSDMenu menu = new USSDMenu(prompt);
		todos.forEach(t -> {
			menu.addMenuOption(REL_PATH + "/view?todoUid=" + t.getUid(), lengthControlledEntry(t, user));
		});
		return menu;
	}

	// should not hit this with page > 0 but being robust because USSD
	private USSDMenu pageEmptyWithSwitchOption(int currentPageNumber, boolean onCreatedOnlyView, String backUrl, User user) {
		final String keySuffix = currentPageNumber == 0 ? "0" : "N";
		final String prompt = onCreatedOnlyView ?
				messageAssembler.getMessage("todo.list.created.empty" + keySuffix, user) :
				messageAssembler.getMessage("todo.list.all.empty" + keySuffix, user);

		USSDMenu menu = new USSDMenu(prompt);
		if (onCreatedOnlyView) {
			menu.addMenuOption(REL_PATH + "/existing?fetchAll=true", messageAssembler.getMessage("todo.list.empty.viewall", user));
		}
		menu.addMenuOption(REL_PATH + "/start", messageAssembler.getMessage("todo.list.empty.createnew", user));
		menu.addMenuOption(backUrl, messageAssembler.getMessage("options.back", user));
		return menu;
	}

	private String determineBackMenu(int page, boolean fetchingAll) {
		return page != 0 ? REL_PATH + "/existing?fetchAll=" + fetchingAll + "&page=" + (page - 1) :
				(fetchingAll ? REL_PATH + "/existing?fetchAll=false&page=0" : startMenu);
	}

	private USSDMenu listCreatedTodos(User user, PageRequest pageRequest, String backUrl) {
		Page<Todo> todosCreatedByUser = todoBroker.fetchPageOfTodosForUser(user.getUid(), true, pageRequest);
		if (!todosCreatedByUser.hasContent()) {
			return pageRequest.getPageNumber() == 0 ? listAllUserTodos(user, pageRequest, false, backUrl) :
					pageEmptyWithSwitchOption(pageRequest.getPageNumber(), true, backUrl, user);
		} else {
			USSDMenu menu = listTodos(messageAssembler.getMessage("todo.list.created.prompt", user), todosCreatedByUser, user);
			menu.addMenuOption(REL_PATH + "/existing?fetchAll=true", messageAssembler.getMessage("todo.list.options.all", user));
			menu.addMenuOption(backUrl, messageAssembler.getMessage("options.back", user));
			return menu;
		}
	}

	private USSDMenu errorMenuTimeInPast(User user, TodoRequest request, String formattedDueDate) {
		return new USSDMenu(messageAssembler.getMessage("todo.confirm.error.past", new String[]{formattedDueDate}, user),
				REL_PATH + "/create/confirm?storedUid=" + request.getUid());
	}

	private LocalDateTime handleUserDateTimeInput(String userInput) {
		if ("1".equalsIgnoreCase(userInput)) {
			return ZonedDateTime.now(DateTimeUtil.getSAST()).plus(30, ChronoUnit.MINUTES).toLocalDateTime();
		} else {
			String formattedDateString = reformatDateInput(userInput);
			LocalDateTime dueDateTime; // try process as regex, if fail, hand over to Selo, and if that fails, default to 1 week
			try {
				dueDateTime = convertDateStringToLocalDateTime(formattedDateString, 12, 30);
			} catch (IllegalArgumentException e) {
				log.info("couldn't convert through default format, trying learning service, with string = {}", formattedDateString);
				try {
					dueDateTime = learningService.parse(formattedDateString);
				} catch (SeloParseDateTimeFailure | SeloApiCallFailure t) {
					dueDateTime = LocalDateTime.now().plus(1, ChronoUnit.WEEKS);
				}
			}
			return dueDateTime;
		}
	}

	private String saveRequestUrl(String menu, String requestUid, String priorInput) {
		return REL_PATH + menu + "?storedUid=" + requestUid + (priorInput == null ? "" :
				"&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
	}

	private String getKeyForSubject(TodoType type) {
		switch (type) {
			case ACTION_REQUIRED:
				return "action";
			case INFORMATION_REQUIRED:
				return "info";
			case VOLUNTEERS_NEEDED:
				return "volunteer";
			default:
				return "action";
		}
	}

	private USSDMenu outOfTodosMenu(User user) {
		USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.limit.prompt", user));
		menu.addMenuOption(REL_PATH + "/create", messageAssembler.getMessage("todo.limit.options.back", user));
		menu.addMenuOptions(ussdSupport.optionsHomeExit(user, true));
		return menu;
	}

	private boolean isWithinAnHour(TodoRequest request) {
		log.debug("checking todo request, action date = {}, now = {}, now + 1 hour = {}", request.getActionByDate(),
				Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
		return request.getActionByDate() != null &&
				request.getActionByDate().isAfter(Instant.now()) &&
				request.getActionByDate().isBefore(Instant.now().plus(1, ChronoUnit.HOURS));
	}

	private String lengthControlledEntry(final Todo entry, final User user) {
		final StringBuilder sb = new StringBuilder();
		int maxLength = 30;
		final String[] words = entry.getMessage().split(" ");
		for (String word : words) {
			if ((sb.length() + word.length() + 1) > maxLength) {
				break;
			} else {
				sb.append(word).append(" ");
			}
		}

		return messageAssembler.getMessage("todo.list.entry",
				new String[]{sb.toString(), ussdSupport.shortDateFormat.format(entry.getCreatedDateTimeAtSAST())}, user);
	}

	private String saveModifyUrl(String modifyMenu, String requestUid, String priorInput) {
		return REL_PATH + "/modify" + modifyMenu + "?requestUid=" + requestUid +
				(StringUtils.isEmpty(priorInput) ? "" : "&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
	}

	private USSDMenu addBackHomeExit(USSDMenu menu, String todoUid, User user) {
		menu.addMenuOption(REL_PATH + "/view?todoUid=" + todoUid, messageAssembler.getMessage("options.back", user));
		menu.addMenuOptions(ussdSupport.optionsHomeExit(user, true));
		return menu;
	}
}
