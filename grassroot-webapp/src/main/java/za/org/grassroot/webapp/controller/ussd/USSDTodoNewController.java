package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoRequest;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.task.TodoBrokerNew;
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

import static za.org.grassroot.core.util.DateTimeUtil.*;

@Slf4j
@RestController
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDTodoNewController extends USSDBaseController {

    private final TodoBrokerNew todoBrokerNew;
    private final TodoRequestBroker todoRequestBroker;
    private final USSDMessageAssembler messageAssembler;
    private final USSDGroupUtil groupUtil;
    private final LearningService learningService;

    private static final String thisPath = "/todo2";
    private static final String fullPath = homePath + "todo2/";

    public USSDTodoNewController(TodoBrokerNew todoBrokerNew, TodoRequestBroker todoRequestBroker, USSDMessageAssembler messageAssembler, USSDGroupUtil groupUtil, LearningService learningService) {
        this.todoBrokerNew = todoBrokerNew;
        this.todoRequestBroker = todoRequestBroker;
        this.messageAssembler = messageAssembler;
        this.groupUtil = groupUtil;
        this.learningService = learningService;
    }

    public USSDMenu respondToTodo(User user, EntityForUserResponse entity) {
        Todo todo = (Todo) entity;
        switch (todo.getType()) {
            case INFORMATION_REQUIRED:
                final String infoPrompt = messageAssembler.getMessage("todo2.info.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                return new USSDMenu(infoPrompt, thisPath + "/respond/info?todoUid=" + todo.getUid());
            case VOLUNTEERS_NEEDED:
                final String volunteerPrompt = messageAssembler.getMessage("todo2.volunteer.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                return new USSDMenu(volunteerPrompt, optionsYesNo(user, thisPath + "/respond/volunteer?todoUid=" + todo.getUid()));
            case VALIDATION_REQUIRED:
                final String confirmationPrompt = messageAssembler.getMessage("todo2.validate.prompt",
                        new String[] { todo.getCreatorAlias(), todo.getMessage() }, user);
                USSDMenu menu = new USSDMenu(confirmationPrompt);
                menu.addMenuOptions(optionsYesNo(user, thisPath + "/respond/validate?todoUid=" + todo.getUid()));
                menu.addMenuOption(thisPath + "/respond/validate?todoUid=" + todo.getUid() + " &" + yesOrNoParam + "=unsure",
                        getMessage("todo2.validate.option.unsure", user));
                return menu;
            default:
                throw new TodoTypeMismatchException();
        }
    }

    @RequestMapping(fullPath + "/respond/volunteer")
    public Request volunteerResponse(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        todoBrokerNew.recordResponse(user.getUid(), todoUid, userResponse, false);
        // todo : if user responds yes, allow them to share? in case yes, leaving a little duplication in here
        String promptMessage = "yes".equalsIgnoreCase(userResponse) ?
                getMessage("todo2.volunteer.yes.prompt", user) :
                getMessage("todo2.volunteer.no.prompt", user);
        return menuBuilder(welcomeMenu(promptMessage, user));
    }

    @RequestMapping(fullPath + "/respond/info")
    public Request confirmInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                       @RequestParam String todoUid,
                                       @RequestParam(value = userInputParam) String userResponse,
                                       @RequestParam(required = false) String priorInput) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info", todoUid, userInput));
        USSDMenu menu = new USSDMenu(getMessage(USSDSection.TODO2, "info", promptKey + ".confirm", userInput, user));
        menu.addMenuOption(thisPath + "/respond/info/confirmed?todoUid=" + todoUid + "&response=" + userInput,
                getMessage("options.yes", user));
        menu.addMenuOption(thisPath + "/respond/info/revise", getMessage("todo2.info.response.change", user));
        return menuBuilder(menu);
    }

    @RequestMapping(fullPath + "/respond/info/confirmed")
    public Request recordInfoResponse(@RequestParam(value = phoneNumber) String msisdn,
                                      @RequestParam String todoUid,
                                      @RequestParam String response) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        todoBrokerNew.recordResponse(user.getUid(), todoUid, response, false);
        return menuBuilder(welcomeMenu(getMessage("todo2.info.prompt.done", user), user));
    }

    @RequestMapping(fullPath + "/respond/info/revise")
    public Request reviseInfoRequest(@RequestParam(value = phoneNumber) String msisdn,
                                     @RequestParam String todoUid,
                                     @RequestParam(value = userInputParam) String userResponse,
                                     @RequestParam(required = false) String priorInput) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? userResponse : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveUrl("/respond/info/revise", todoUid, userInput));
        // note: probably want to come back & test whether to re-include original request
        final String prompt = messageAssembler.getMessage("todo2.info.revise.prompt", new String[] { userInput },  user);
        return menuBuilder(new USSDMenu(prompt, thisPath + "/respond/info?todoUid=" + todoUid));
    }

    // note : ask when it was done?
    @RequestMapping(fullPath + "/respond/validate")
    public Request validateTodoCompletion(@RequestParam(value = phoneNumber) String msisdn,
                                          @RequestParam String todoUid,
                                          @RequestParam(value = yesOrNoParam) String userResponse) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (!"unsure".equalsIgnoreCase(userResponse)) {
            todoBrokerNew.recordResponse(user.getUid(), todoUid, userResponse, false);
        }
        return menuBuilder(welcomeMenu(getMessage("todo2.validate." + userResponse + ".prompt", user), user));
    }

    private String saveUrl(String menu, String todoUid, String userInput) {
        return thisPath + menu + "?todoUid=" + todoUid + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
    }

    /*
    Creating todos - start with the open menu, with option to view old
     */

    @RequestMapping(fullPath + "/start")
    public Request start(@RequestParam(value = phoneNumber) String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = new USSDMenu(messageAssembler.getMessage("todo2.start.prompt", user));
        menu.addMenuOption(thisPath + "/create?type=" + TodoType.ACTION_REQUIRED,
                messageAssembler.getMessage("todo2.start.options.action", user));
        menu.addMenuOption(thisPath + "/create?type=" + TodoType.VOLUNTEERS_NEEDED,
                messageAssembler.getMessage("todo2.start.options.volunteer", user));
        menu.addMenuOption(thisPath + "/create?type=" + TodoType.INFORMATION_REQUIRED,
                messageAssembler.getMessage("todo2.start.options.info", user));
        menu.addMenuOption(thisPath + "/existing", messageAssembler.getMessage("todo2.start.options.existing", user));
        return menuBuilder(menu);
    }

    @RequestMapping(fullPath + "/create")
    public Request create(@RequestParam(value = phoneNumber) String msisdn,
                          @RequestParam TodoType type,
                          @RequestParam(required = false) String storedUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        final String requestUid = StringUtils.isEmpty(storedUid) ?
                todoRequestBroker.create(user.getUid(), type).getUid() : storedUid;
        cacheManager.putUssdMenuForUser("/create", requestUid);
        USSDGroupUtil.GroupMenuBuilder groupMenu = new USSDGroupUtil.GroupMenuBuilder(user, USSDSection.TODO2)
                .messageKey("group").urlForExistingGroup("/create/subject?storedUid=" + requestUid);
        return menuBuilder(groupUtil.askForGroup(groupMenu));
    }

    @RequestMapping(fullPath + "/create/subject")
    public Request askForSubject(@RequestParam(value = phoneNumber) String msisdn,
                                 @RequestParam String storedUid,
                                 @RequestParam(required = false) String groupUid,
                                 @RequestParam(required = false) Boolean revising) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveRequestUrl("/create/subject", storedUid, null));
        if (!StringUtils.isEmpty(groupUid)) {
            todoRequestBroker.updateGroup(user.getUid(), storedUid, groupUid);
        }
        TodoRequest todoRequest = todoRequestBroker.load(storedUid);
        final String prompt = messageAssembler.getMessage("todo2.msg." + getKeyForSubject(todoRequest.getType()) + ".prompt", user);
        return menuBuilder(new USSDMenu(prompt, revising == null || !revising
                ? thisPath + "/create/deadline?storedUid=" + storedUid
                : thisPath + "/create/confirm?field=subject&storedUid=" + storedUid));
    }

    @RequestMapping(fullPath + "/create/deadline")
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
        final String prompt = messageAssembler.getMessage("todo2." + getKeyForSubject(todoRequest.getType()) + ".deadline", user);
        final String nextUrl = TodoType.INFORMATION_REQUIRED.equals(todoRequest.getType()) && (revising == null || !revising) ?
                thisPath + "/create/tag?storedUid=" + storedUid :
                thisPath + "/create/confirm?storedUid=" + storedUid;
        return menuBuilder(new USSDMenu(prompt, nextUrl));
    }

    @RequestMapping(fullPath + "/create/tag")
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
        final String prompt = messageAssembler.getMessage("todo2.info.tag.prompt", user);
        return menuBuilder(new USSDMenu(prompt, thisPath + "/create/confirm?storedUid=" + storedUid + "&field=tag"));
    }

    @RequestMapping(fullPath + "/create/confirm")
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
        String formattedDueDate = dateTimeFormat.format(convertToUserTimeZone(request.getActionByDate(), getSAST()));

        if (request.getActionByDate() != null && request.getActionByDate().isBefore(Instant.now())) {
            return menuBuilder(errorMenuTimeInPast(user, request, formattedDueDate));
        } else {
            String[] promptFields = new String[]{request.getMessage(), request.getParent().getName(), formattedDueDate};
            final String promptKey = isWithinAnHour(request) ?
                    "todo2." + getKeyForSubject(request.getType()) + ".confirm" :
                    "todo2." + getKeyForSubject(request.getType()) + ".confirm.instant";
            USSDMenu menu = new USSDMenu(messageAssembler.getMessage(promptKey, promptFields, user));
            menu.addMenuOption(thisPath + "/create/complete?storedUid=" + request.getUid(),
                    messageAssembler.getMessage("options.yes", user));
            menu.addMenuOption(thisPath + "/create/subject?storedUid=" + request.getUid() + "&revising=true",
                    messageAssembler.getMessage("todo2.confirm.options.subject", user));
            menu.addMenuOption(thisPath + "/create/deadline?storedUid=" + request.getUid() + "&revising=true",
                    messageAssembler.getMessage("todo2.confirm.options.date", user));
            if (TodoType.INFORMATION_REQUIRED.equals(request.getType())) {
                menu.addMenuOption(thisPath + "/create/tag?storedUid=" + request.getUid() + "&revising=true",
                        messageAssembler.getMessage("todo2.confirm.options.tag", user));
            }

            return menuBuilder(menu);
        }
    }

    @RequestMapping(fullPath + "/create/complete")
    @ResponseBody
    public Request finishTodoEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam String storedUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        todoRequestBroker.finish(storedUid);
        return menuBuilder(new USSDMenu(messageAssembler.getMessage("todo2.done.prompt", user), optionsHomeExit(user, false)));
    }

    private USSDMenu errorMenuTimeInPast(User user, TodoRequest request, String formattedDueDate) {
        return new USSDMenu(messageAssembler.getMessage("todo2.confirm.error.past", new String[] { formattedDueDate }, user),
                thisPath + "/create/confirm?storedUid=" + request.getUid());
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
        return request.getActionByDate() != null &&
                request.getActionByDate().isAfter(Instant.now()) &&
                request.getActionByDate().isBefore(Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private String saveRequestUrl(String menu, String requestUid, String priorInput) {
        return thisPath + menu + "?storedUid=" + requestUid + (priorInput == null ? "" :
                "&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
    }

}
