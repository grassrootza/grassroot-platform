package za.org.grassroot.webapp.controller.ussd;

import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.domain.task.TodoRequest;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.services.exception.TodoDeadlineNotInFutureException;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.group.MemberDataExportBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.TodoRequestBroker;
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

@Slf4j @RestController
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDTodoController extends USSDBaseController {

    private static final String REL_PATH = "/todo";
    private static final String FULL_PATH = homePath + "todo/";
    private static final int PAGE_SIZE = 3;

    private final TodoBroker todoBroker;
    private final TodoRequestBroker todoRequestBroker;
    private final MemberDataExportBroker dataExportBroker;
    private final LearningService learningService;

    private USSDMessageAssembler messageAssembler;
    @Setter(AccessLevel.PACKAGE) private USSDGroupUtil groupUtil;

    @Autowired
    public USSDTodoController(TodoBroker todoBroker, TodoRequestBroker todoRequestBroker, MemberDataExportBroker dataExportBroker, USSDGroupUtil groupUtil, LearningService learningService) {
        this.todoBroker = todoBroker;
        this.todoRequestBroker = todoRequestBroker;
        this.dataExportBroker = dataExportBroker;
        this.groupUtil = groupUtil;
        this.learningService = learningService;
    }


    @Autowired
    public void setMessageAssembler(USSDMessageAssembler messageAssembler) {
        this.messageAssembler = messageAssembler;
        super.setMessageAssembler(messageAssembler); // else get nulls ... need to fix this properly soon
    }

    public USSDMenu respondToTodo(User user, EntityForUserResponse entity) {

        Todo todo = (Todo) entity;
        switch (todo.getType()) {
            case INFORMATION_REQUIRED:
                final String infoPrompt = messageAssembler.getMessage("todo.info.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                return new USSDMenu(infoPrompt, REL_PATH + "/respond/info?todoUid=" + todo.getUid());
            case VOLUNTEERS_NEEDED:
                final String volunteerPrompt = messageAssembler.getMessage("todo.volunteer.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                return new USSDMenu(volunteerPrompt, optionsYesNo(user, REL_PATH + "/respond/volunteer?todoUid=" + todo.getUid()));
            case VALIDATION_REQUIRED:
                final String confirmationPrompt = messageAssembler.getMessage("todo.validate.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                USSDMenu menu = new USSDMenu(confirmationPrompt);
                menu.addMenuOptions(optionsYesNo(user, REL_PATH + "/respond/validate?todoUid=" + todo.getUid()));
                menu.addMenuOption(REL_PATH + "/respond/validate?todoUid=" + todo.getUid() + " &" + yesOrNoParam + "=unsure",
                        messageAssembler.getMessage("todo.validate.option.unsure", user));
                return menu;
            default:
                throw new TodoTypeMismatchException();
        }
    }

    @RequestMapping(value = FULL_PATH + "/respond/volunteer", method = RequestMethod.GET)
    public Request volunteerResponse(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        todoBroker.recordResponse(user.getUid(), todoUid, userResponse, false);
        // todo : if user responds yes, allow them to share? in case yes, leaving a little duplication in here
        String promptMessage = "yes".equalsIgnoreCase(userResponse) ?
                messageAssembler.getMessage("todo.volunteer.yes.prompt", user) :
                messageAssembler.getMessage("todo.volunteer.no.prompt", user);
        log.info("User={},Prompt={},todo uid={},response={}",user,promptMessage,todoUid,userResponse);
        return menuBuilder(welcomeMenu(promptMessage, user));
    }

    @RequestMapping(value = FULL_PATH + "/respond/info", method = RequestMethod.GET)
    public Request confirmInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                       @RequestParam String todoUid,
                                       @RequestParam(value = userInputParam) String userResponse,
                                       @RequestParam(required = false) String priorInput) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info", todoUid, userInput));
        log.info("User input={},User={}",userInput,user);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage(USSDSection.TODO, "info", promptKey + ".confirm", userInput, user));
        menu.addMenuOption(REL_PATH + "/respond/info/confirmed?todoUid=" + todoUid +
                        "&response=" + USSDUrlUtil.encodeParameter(userInput), messageAssembler.getMessage("options.yes", user));
        menu.addMenuOption(REL_PATH + "/respond/info/revise", messageAssembler.getMessage("todo.info.response.change", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/respond/info/confirmed", method = RequestMethod.GET)
    public Request recordInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                      @RequestParam String todoUid,
                                      @RequestParam String response) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        todoBroker.recordResponse(user.getUid(), todoUid, response, false);
        return menuBuilder(welcomeMenu(messageAssembler.getMessage("todo.info.prompt.done", user), user));
    }

    @RequestMapping(value = FULL_PATH + "/respond/info/revise", method = RequestMethod.GET)
    public Request reviseInfoRequest(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam(value = userInputParam) String userResponse,
                                     @RequestParam(required = false) String priorInput) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info/revise", todoUid, userInput));
        // note: probably want to come back & test whether to re-include original request
        final String prompt = messageAssembler.getMessage("todo.info.revise.prompt", new String[] { userInput },  user);
        log.info("prompt={},user={},user input={}",prompt,user,userInput);
        return menuBuilder(new USSDMenu(prompt, REL_PATH + "/respond/info?todoUid=" + todoUid));
    }

    // note : ask when it was done?
    @RequestMapping(value = FULL_PATH + "/respond/validate", method = RequestMethod.GET)
    public Request validateTodoCompletion(@RequestParam(value = phoneNumber) String msisdn,
                                          @RequestParam String todoUid,
                                          @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (!"unsure".equalsIgnoreCase(userResponse)) {
            todoBroker.recordResponse(user.getUid(), todoUid, userResponse, false);
        }
        log.info("User={},response={}",user,userResponse);
        return menuBuilder(welcomeMenu(messageAssembler.getMessage("todo.validate." + userResponse + ".prompt", user), user));
    }

    private String saveUrl(String menu, String todoUid, String userInput) {
        return REL_PATH + menu + "?todoUid=" + todoUid + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
    }

    /*
    Creating todos - start with the open menu, with option to view old
     */

    @RequestMapping(value = FULL_PATH + "/start", method = RequestMethod.GET)
    public Request start(@RequestParam(value = phoneNumber) String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.start.prompt", user));
        menu.addMenuOption(REL_PATH + "/create?type=" + ACTION_REQUIRED,
                messageAssembler.getMessage("todo.start.options.action", user));
        menu.addMenuOption(REL_PATH + "/create?type=" + VOLUNTEERS_NEEDED,
                messageAssembler.getMessage("todo.start.options.volunteer", user));
        menu.addMenuOption(REL_PATH + "/create?type=" + INFORMATION_REQUIRED,
                messageAssembler.getMessage("todo.start.options.info", user));
        menu.addMenuOption(REL_PATH + "/existing", messageAssembler.getMessage("todo.start.options.existing", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/create", method = RequestMethod.GET)
    public Request create(@RequestParam(value = phoneNumber) String msisdn,
                          @RequestParam TodoType type,
                          @RequestParam(required = false) String storedUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        final String requestUid = StringUtils.isEmpty(storedUid) ?
                todoRequestBroker.create(user.getUid(), type).getUid() : storedUid;
        cacheManager.putUssdMenuForUser("/create", requestUid);
        USSDGroupUtil.GroupMenuBuilder groupMenu = new USSDGroupUtil.GroupMenuBuilder(user, USSDSection.TODO)
                .messageKey("group").urlForExistingGroup("/create/subject?storedUid=" + requestUid);
        return menuBuilder(groupUtil.askForGroup(groupMenu));
    }

    @RequestMapping(value = FULL_PATH + "/create/subject", method = RequestMethod.GET)
    public Request askForSubject(@RequestParam(value = phoneNumber) String msisdn,
                                 @RequestParam String storedUid,
                                 @RequestParam(required = false) String groupUid,
                                 @RequestParam(required = false) Boolean revising) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/create/subject", storedUid, null));
        if (!StringUtils.isEmpty(groupUid)) {
            todoRequestBroker.updateGroup(user.getUid(), storedUid, groupUid);
        }
        TodoRequest todoRequest = todoRequestBroker.load(storedUid);
        final String prompt = messageAssembler.getMessage("todo.msg." + getKeyForSubject(todoRequest.getType()) + ".prompt", user);
        log.info("todo req={},user={},prompt={}",todoRequest,user,prompt);
        return menuBuilder(new USSDMenu(prompt, revising == null || !revising
                ? REL_PATH + "/create/deadline?storedUid=" + storedUid
                : REL_PATH + "/create/confirm?field=subject&storedUid=" + storedUid));
    }

    @RequestMapping(value = FULL_PATH + "/create/deadline", method = RequestMethod.GET)
    public Request askForDeadline(@RequestParam(value = phoneNumber) String msisdn,
                                  @RequestParam String storedUid,
                                  @RequestParam(value = userInputParam) String userInput,
                                  @RequestParam(required = false) String priorInput,
                                  @RequestParam(required = false) Boolean revising) throws URISyntaxException {
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
        return menuBuilder(new USSDMenu(prompt, nextUrl));
    }

    @RequestMapping(value = FULL_PATH + "/create/tag", method = RequestMethod.GET)
    public Request askForResponseTag(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String storedUid,
                                     @RequestParam(value = userInputParam) String userInput,
                                     @RequestParam(required = false) String priorInput,
                                     @RequestParam(required = false) Boolean revising) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/create/tag", storedUid, priorInput));
        if (revising == null || !revising) {
            final String dateTime = StringUtils.isEmpty(priorInput) ? userInput : priorInput;
            todoRequestBroker.updateDueDate(user.getUid(), storedUid, handleUserDateTimeInput(dateTime));
        }
        final String prompt = messageAssembler.getMessage("todo.info.tag.prompt", user);
        return menuBuilder(new USSDMenu(prompt, REL_PATH + "/create/confirm?storedUid=" + storedUid + "&field=tag"));
    }

    @RequestMapping(value = FULL_PATH + "/create/confirm", method = RequestMethod.GET)
    public Request confirmTodoCreation(@RequestParam(value = phoneNumber) String msisdn,
                                       @RequestParam String storedUid,
                                       @RequestParam(value = userInputParam) String userInput,
                                       @RequestParam(required = false) String priorInput,
                                       @RequestParam(required = false) String field) throws URISyntaxException {
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

        log.info("User={},todo request={}",user,request);

        log.info("todo request reloaded = {}", request);

        String formattedDueDate = dateTimeFormat.format(convertToUserTimeZone(request.getActionByDate(), getSAST()));

        if (request.getActionByDate() != null && request.getActionByDate().isBefore(Instant.now())) {
            return menuBuilder(errorMenuTimeInPast(user, request, formattedDueDate));
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

            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = FULL_PATH + "/create/complete", method = RequestMethod.GET)
    @ResponseBody
    public Request finishTodoEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam String storedUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        todoRequestBroker.finish(storedUid);
        log.info("User={},storeuid={}",user,storedUid);
        return menuBuilder(new USSDMenu(messageAssembler.getMessage("todo.done.prompt", user), optionsHomeExit(user, false)));
    }

    private USSDMenu errorMenuTimeInPast(User user, TodoRequest request, String formattedDueDate) {
        return new USSDMenu(messageAssembler.getMessage("todo.confirm.error.past", new String[] { formattedDueDate }, user),
                REL_PATH + "/create/confirm?storedUid=" + request.getUid());
    }

    /*
    Menus to view prior todos
     */

    @RequestMapping(value = FULL_PATH + "/existing", method = RequestMethod.GET)
    public Request viewExistingTodos(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(required = false) Integer page,
                                     @RequestParam(required = false) Boolean fetchAll) throws URISyntaxException {
        int pageNumber = page == null ? 0 : page;
        boolean fetchAllTodos = fetchAll == null ? false : fetchAll;

        PageRequest pageRequest = new PageRequest(pageNumber, PAGE_SIZE, Sort.Direction.DESC, "createdDateTime");
        User user = userManager.findByInputNumber(inputNumber, REL_PATH + "/existing?page=" + pageNumber + "&fetchAll=" + fetchAllTodos);
        final String backUrl = determineBackMenu(pageNumber, fetchAllTodos);

        return menuBuilder(!fetchAllTodos
                ? listCreatedTodos(user, pageRequest, backUrl)
                : listAllUserTodos(user, pageRequest, pageNumber == 0, backUrl));
    }

    private String determineBackMenu(int page, boolean fetchingAll) {
        return page != 0 ? REL_PATH + "/existing?fetchAll=" + fetchingAll + "&page=" + (page - 1) :
                (fetchingAll ? REL_PATH + "/existing?fetchAll=false&page=0" : startMenu);
    }

    private USSDMenu listCreatedTodos(User user, PageRequest pageRequest, String backUrl) {
        Page<Todo> todosCreatedByUser = todoBroker.fetchPageOfTodosForUser(user.getUid(), true, false, pageRequest);
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

    private USSDMenu listAllUserTodos(User user, PageRequest pageRequest, boolean offerCreatedOnly, String backUrl) {
        Page<Todo> todosForUser = todoBroker.fetchPageOfTodosForUser(user.getUid(), false, false, pageRequest);
        log.info("User todos={}",todosForUser);
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

    /*
    View and modify a to-do entry
     */
    @RequestMapping(value = FULL_PATH + "/view", method = RequestMethod.GET)
    public Request viewTodoEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        Todo todo = todoBroker.load(todoUid);

        final String urlSuffix = "?todoUid=" + todo.getUid();
        List<TodoAssignment> todoAssignments = todoBroker.fetchAssignedUserResponses(user.getUid(), todoUid, true,
                false, false);

        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.view.prompt.desc", new String[] {
                todo.getMessage(),
                todo.getParent().getName(),
                shortDateFormat.format(todo.getDeadlineTimeAtSAST()),
                String.valueOf(todoAssignments.size())
        }, user));

        if (todo.getCreatedByUser().equals(user)) {
            menu.addMenuOption(REL_PATH + "/view/email" + urlSuffix, messageAssembler.getMessage("todo.view.options.email", user));
            menu.addMenuOption(REL_PATH + "/modify/complete" + urlSuffix, messageAssembler.getMessage("todo.view.options.complete", user));
            menu.addMenuOption(REL_PATH + "/modify/" + urlSuffix, messageAssembler.getMessage("todo.view.options.modify", user));
        } else if (todoAssignments.stream().anyMatch(ta -> ta.getUser().equals(user))) {
            menu.addMenuOption(REL_PATH + "/respond" + urlSuffix,
                    messageAssembler.getMessage("todo.view.options.response.change", user));
        } else if (todoBroker.canUserRespond(user.getUid(), todoUid)) {
            menu.addMenuOption(REL_PATH + "/respond" + urlSuffix,
                    messageAssembler.getMessage("todo.view.options.response.enter", user));
        }

        menu.addMenuOption(REL_PATH + "/existing", messageAssembler.getMessage("options.back", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/view/email", method = RequestMethod.GET)
    public Request emailTodoResponses(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, REL_PATH + "/view/email?todoUid=" + todoUid);
        final String messagePrompt = user.hasEmailAddress() ?
                messageAssembler.getMessage("todo.email.prompt.existing", user) :
                messageAssembler.getMessage("todo.email.prompt.none", user);
        return menuBuilder(new USSDMenu(messagePrompt, REL_PATH + "/view/email/send?todoUid=" + todoUid));
    }

    @RequestMapping(value = FULL_PATH + "/view/email/send", method = RequestMethod.GET)
    public Request emailResponsesDo(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam String todoUid,
                                    @RequestParam(value = userInputParam) String emailAddress) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        log.info("User={}",user);
        boolean isEmailValid = EmailValidator.getInstance().isValid(emailAddress);
        if (isEmailValid || user.hasEmailAddress() && emailAddress.length() == 1) {
            final String emailToPass = emailAddress.length() == 1 ? user.getEmailAddress() : emailAddress;
            dataExportBroker.emailTodoResponses(user.getUid(), todoUid, emailToPass);
            USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.email.prompt.done", user));
            return menuBuilder(addBackHomeExit(menu, todoUid, user));
        } else {
            String prompt = messageAssembler.getMessage("todo.email.prompt.error", new String[] { emailAddress }, user);
            return menuBuilder(new USSDMenu(prompt, REL_PATH + "/view/email/send?todoUid=" + todoUid));
        }
    }


    @RequestMapping(value = FULL_PATH + "/modify/complete", method = RequestMethod.GET)
    public Request markTodoCompletePrompt(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.complete.prompt", user));
        menu.addMenuOption(REL_PATH + "/complete/done?todoUid=" + todoUid, messageAssembler.getMessage("options.yes", user));
        menu.addMenuOption(REL_PATH + "/view?toodUid=" + todoUid, messageAssembler.getMessage("options.back", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "modify/complete/done", method = RequestMethod.GET)
    public Request markTodoCompleteDone(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        todoBroker.updateTodoCompleted(user.getUid(), todoUid, true);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.complete.done", user));
        return menuBuilder(addBackHomeExit(menu, todoUid, user));
    }

    @RequestMapping(value = FULL_PATH + "/modify", method = RequestMethod.GET)
    public Request alterTodoMenu(@RequestParam(value = phoneNumber) String msisdn,
                                 @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/", todoUid, null));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.change.prompt", new String[] { todo.getName() }, user));
        final String keyRoot = "ussd.todo.change.options";
        menu.addMenuOption(REL_PATH + "/modify/subject?todoUid=" + todoUid,
                messageAssembler.getMessage(keyRoot + ".subject", user));
        menu.addMenuOption(REL_PATH + "/modify/date?todoUid=" + todoUid,
                messageAssembler.getMessage(keyRoot + ".duedate", user));
        menu.addMenuOption(REL_PATH + "/modify/cancel?todoUid=" + todoUid,
                messageAssembler.getMessage(keyRoot + ".cancel", user));
        menu.addMenuOption(REL_PATH + "/view?todoUid=" + todoUid,
                messageAssembler.getMessage(optionsKey + "back", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/modify/subject", method = RequestMethod.GET)
    public Request alterTodoSubject(@RequestParam(value = phoneNumber) String msisdn,
                                    @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/subject", todoUid, null));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.modify.subject.prompt", new String[] { todo.getName() }, user),
                REL_PATH + "/modify/subject/done?todoUid=" + todoUid);
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/modify/subject/done", method = RequestMethod.GET)
    public Request confirmSubjectModification(@RequestParam(value = phoneNumber) String msisdn,
                                              @RequestParam String todoUid,
                                              @RequestParam(value = userInputParam) String subject) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        if (!StringUtils.isEmpty(subject) && subject.length() > 0) {
            todoBroker.updateSubject(user.getUid(), todoUid, subject);
            USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.modified.done", user));
            return menuBuilder(addBackHomeExit(menu, todoUid, user));
        } else {
            USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.subject.error", new String[] { subject }, user),
                    REL_PATH + "/modify/subject/done?todoUid=" + todoUid);
            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = FULL_PATH + "/modify/date", method = RequestMethod.GET)
    public Request alterTodoDate(@RequestParam(value = userInputParam) String msisdn,
                                 @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/date", todoUid, null));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(
                messageAssembler.getMessage("todo.modify.date.prompt", new String[] { dateFormat.format(todo.getActionByDateAtSAST()) }, user),
                REL_PATH + "/modify/date/confirm?todoUid=" + todoUid);
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/modify/date/confirm", method = RequestMethod.GET)
    public Request alterDateConfirm(@RequestParam(value = phoneNumber) String msisdn,
                                    @RequestParam String todoUid,
                                    @RequestParam(value = userInputParam) String userInput,
                                    @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {
        final String enteredValue = priorInput == null ? userInput : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/date/confirm", todoUid, enteredValue));
        LocalDateTime parsedDateTime = handleUserDateTimeInput(enteredValue);
        log.info("ParsedDateTime={},User={}",parsedDateTime,user);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("changing this", new String[] { dateFormat.format(parsedDateTime) }, user));
        final String confirmUrl = REL_PATH + "/modify/date/complete?todoUid=" + todoUid + "&epochMillis="
                + DateTimeUtil.convertToSystemTime(parsedDateTime, DateTimeUtil.getSAST());
        menu.addMenuOption(confirmUrl, messageAssembler.getMessage("options.yes", user));
        menu.addMenuOption(REL_PATH + "/modify/date?todoUid=" + todoUid, messageAssembler.getMessage("options.no", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/modify/date/complete", method = RequestMethod.GET)
    public Request changeEntryConfirmed(@RequestParam String msisdn,
                                        @RequestParam String todoUid,
                                        @RequestParam long epochMillis) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        try {
            todoBroker.extend(user.getUid(), todoUid, Instant.ofEpochMilli(epochMillis));
            USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.modified.done", user));
            return menuBuilder(addBackHomeExit(menu, todoUid, user));
        } catch (TodoDeadlineNotInFutureException e) {
            // todo : catch this during creation too
            ZonedDateTime zdt = DateTimeUtil.convertToUserTimeZone(Instant.ofEpochMilli(epochMillis), DateTimeUtil.getSAST());
            USSDMenu menu = new USSDMenu(
                    messageAssembler.getMessage("todo.date.notfuture", new String[] { dateFormat.format(zdt)}, user),
                    REL_PATH + "/modify/date/confirm?todoUid=" + todoUid);
            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = FULL_PATH + "/modify/cancel", method = RequestMethod.GET)
    public Request cancelTodo(@RequestParam String msisdn, @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveModifyUrl("/cancel", todoUid, null));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.cancel.prompt", new String[] { todo.getName() }, user));
        menu.addMenuOptions(optionsYesNo(user, REL_PATH + "/modify/cancel/confirm?todoUid=" + todoUid));
        log.info("Options={}",menu.getMenuOptions());
        return menuBuilder(menu);
    }

    @RequestMapping(value = FULL_PATH + "/modify/cancel/confirm", method = RequestMethod.GET)
    public Request cancelTodoConfirmed(@RequestParam String msisdn,
                                       @RequestParam String todoUid,
                                       @RequestParam(value = yesOrNoParam) boolean confirmed) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        if (confirmed) {
            todoBroker.cancel(user.getUid(), todoUid, null);
            USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo.cancel.prompt", user));
            menu.addMenuOption(REL_PATH + "/start", messageAssembler.getMessage("todo.cancel.done.todos", user));
            menu.addMenuOptions(optionsHomeExit(user, true));
            return menuBuilder(menu);
        } else {
            return viewTodoEntry(msisdn, todoUid);
        }
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

    private boolean isWithinAnHour(TodoRequest request) {
        log.debug("checking todo request, action date = {}, now = {}, now + 1 hour = {}", request.getActionByDate(),
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS));
        return request.getActionByDate() != null &&
                request.getActionByDate().isAfter(Instant.now()) &&
                request.getActionByDate().isBefore(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private String saveRequestUrl(String menu, String requestUid, String priorInput) {
        return REL_PATH + menu + "?storedUid=" + requestUid + (priorInput == null ? "" :
                "&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
    }

    private String saveModifyUrl(String modifyMenu, String requestUid, String priorInput) {
        return REL_PATH + "/modify" + modifyMenu + "?requestUid=" + requestUid +
                (StringUtils.isEmpty(priorInput) ? "" : "&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
    }

    private String lengthControlledEntry(final Todo entry, final User user) {
        final StringBuilder sb = new StringBuilder();
        int maxLength = 30;
        final String[] words = entry.getMessage().split(" ");
        for (String word : words) {
            if ((sb.length() + word.length() + 1) > maxLength)
                break;
            else
                sb.append(word).append(" ");
        }

        return messageAssembler.getMessage("todo.list.entry",
                new String[] { sb.toString(), shortDateFormat.format(entry.getCreatedDateTimeAtSAST()) }, user);
    }

    private USSDMenu addBackHomeExit(USSDMenu menu, String todoUid, User user) {
        menu.addMenuOption(REL_PATH + "/view?todoUid=" + todoUid, messageAssembler.getMessage("options.back", user));
        menu.addMenuOptions(optionsHomeExit(user, true));
        return menu;
    }

}
