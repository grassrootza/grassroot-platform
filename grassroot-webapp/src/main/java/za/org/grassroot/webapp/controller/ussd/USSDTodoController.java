package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.TodoRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static za.org.grassroot.core.util.DateTimeUtil.convertDateStringToLocalDateTime;
import static za.org.grassroot.core.util.DateTimeUtil.reformatDateInput;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveToDoMenu;

/**
 * Created by luke on 2015/12/15.
 */
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDTodoController extends USSDBaseController {

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Value("${grassroot.ussd.location.enabled:false}")
    private boolean locationRequestEnabled;

    private final PermissionBroker permissionBroker;

    private final TodoBroker todoBroker;

    private final TodoRequestBroker todoRequestBroker;

    private final LearningService learningService;

    private final USSDGroupUtil groupUtil;

    private static final USSDSection thisSection = USSDSection.TODO;
    private static final String path = homePath + todoMenus;

    private static final String groupMenu = "group",
            subjectMenu = "subject",
            instantMenu = "instant",
            dueDateMenu = "due_date",
            pickUserMenu = "pick_user",
            confirmMenu = "confirm",
            send = "send";

    private static final String
            incompleteEntries = "incomplete",
            listEntriesMenu = "list",
            viewEntryMenu = "view",
            viewAssignment = "view_assigned",
            setCompleteMenu = "complete",
            changeEntry = "change",
            alterSubject = "subject_change",
            alterDate = "date_change",
            cancelTodo = "cancel";

    private static final String todoUidParam = "todoUid";
    private static final String todoUrlSuffix = "?" + todoUidParam + "=";
    private static final String priorMenuSuffix = "&" + previousMenu + "=";

    private static final int PAGE_LENGTH = 3;
    private static final int stdHour =13, stdMinute = 0;

    @Autowired
    public USSDTodoController(PermissionBroker permissionBroker, TodoBroker todoBroker, TodoRequestBroker todoRequestBroker, LearningService learningService, USSDGroupUtil groupUtil) {
        this.permissionBroker = permissionBroker;
        this.todoBroker = todoBroker;
        this.todoRequestBroker = todoRequestBroker;
        this.learningService = learningService;
        this.groupUtil = groupUtil;
    }

    private String menuPrompt(String menu, User user) {
        return getMessage(thisSection, menu, promptKey, user);
    }

    private String returnUrl(String nextMenu, String todoUid) {
        return todoMenus + nextMenu + todoUrlSuffix + todoUid;
    }

    /*
    Menus to create to-do
     */


    /**
     * SECTION: Select and view prior to-do entries
     */

    private String truncateEntryDescription(final Todo entry, final User user) {
        final StringBuilder sb = new StringBuilder();
        int maxLength = 30;
        final String[] words = entry.getMessage().split(" ");
        for (String word : words) {
            if ((sb.length() + word.length() + 1) > maxLength)
                break;
            else
                sb.append(word).append(" ");
        }
        return getMessage(thisSection, listEntriesMenu, "entry.format",
                new String[] { sb.toString(), shortDateFormat.format(entry.getActionByDateAtSAST()) }, user);
    }

    @RequestMapping(path + viewEntryMenu)
    @ResponseBody
    public Request viewEntryMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = todoUidParam) String todoUid,
                                 @RequestParam(value = "back", required = false) String backPath) throws URISyntaxException {

        final User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(viewEntryMenu, todoUid));
        final Todo todo = todoBroker.load(todoUid);

        final boolean userMarkedDone = todo.isCompletionConfirmedByMember(user);
        final boolean userAssigned = todo.isAllGroupMembersAssigned() || todo.getAssignedMembers().contains(user);

        final String baseDescription = getMessage(thisSection, viewEntryMenu, promptKey + ".desc",
                new String[] { todo.getMessage(), todo.getParent().getName(), shortDateFormat.format(todo.getActionByDateAtSAST()) }, user);
        final String confirmCount = todo.countCompletions() == 0 ? getMessage(thisSection, viewEntryMenu, promptKey + ".marked.none", user)
                : getMessage(thisSection, viewEntryMenu, promptKey + ".marked.some", String.valueOf(todo.countCompletions()), user);
        final String userResponse = userMarkedDone ? getMessage(thisSection, viewEntryMenu, promptKey + ".user.confirmed", user)
                : getMessage(thisSection, viewEntryMenu, promptKey + ".user.not_conf", user);

        USSDMenu menu = new USSDMenu(String.join(" ", baseDescription, confirmCount, userResponse));
        if (!userMarkedDone && userAssigned) { // note: will need to revisit whether non-assigned members can set entry complete (may want to allow)
            menu.addMenuOption(todoMenus + setCompleteMenu + todoUrlSuffix + todoUid, getMessage(thisSection, viewEntryMenu, optionsKey + "confirm", user));
        }

        if (todo.getCreatedByUser().equals(user)) {
            menu.addMenuOption(todoMenus + changeEntry + todoUrlSuffix + todoUid, getMessage(thisSection, viewEntryMenu, optionsKey + "change", user));
        }

        if (!todo.isAllGroupMembersAssigned()) {
            menu.addMenuOption(todoMenus + viewAssignment + todoUrlSuffix + todoUid, getMessage(thisSection, viewEntryMenu, optionsKey + "assigned", user));
        }

        menu.addMenuOption(todoMenus + (backPath != null ? backPath : startMenu), getMessage("options.back", user));

        return menuBuilder(menu);
    }


    @RequestMapping(path + changeEntry)
    @ResponseBody
    public Request alterTodoMenu(@RequestParam String msisdn, @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveToDoMenu(changeEntry, todoUid));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, changeEntry, promptKey, todo.getName(), user));
        menu.addMenuOption(todoMenus + alterSubject + todoUrlSuffix + todoUid, getMessage(thisSection, changeEntry, optionsKey + "subject", user));
        menu.addMenuOption(todoMenus + alterDate + todoUrlSuffix + todoUid, getMessage(thisSection, changeEntry, optionsKey + "duedate", user));
        menu.addMenuOption(todoMenus + cancelTodo + todoUrlSuffix + todoUid, getMessage(thisSection, changeEntry, optionsKey + "cancel", user));
        menu.addMenuOption(todoMenus + viewEntryMenu + todoUrlSuffix + todoUid, getMessage(optionsKey + "back", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + alterSubject)
    @ResponseBody
    public Request alterTodoSubject(@RequestParam String msisdn, @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveToDoMenu(alterSubject, todoUid));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, changeEntry, promptKey, todo.getName(), user),
                todoMenus + changeEntry + doSuffix + todoUrlSuffix + todoUid + "&field=" + alterSubject);
        return menuBuilder(menu);
    }

    @RequestMapping(path + alterDate)
    @ResponseBody
    public Request alterTodoDate(@RequestParam String msisdn, @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveToDoMenu(alterDate, todoUid));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, changeEntry, promptKey, dateFormat.format(todo.getActionByDateAtSAST()), user),
                todoMenus + alterDate + "-confirm" + todoUrlSuffix + todoUid);
        return menuBuilder(menu);
    }

    @RequestMapping(path + alterDate + "-confirm")
    @ResponseBody
    public Request alterDateConfirm(@RequestParam String msisdn, @RequestParam String todoUid,
                                    @RequestParam(value = userInputParam) String userInput,
                                    @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {
        final String enteredValue = priorInput == null ? userInput : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveToDoMenu(alterDate + "-confirm", todoUid, enteredValue));
        LocalDateTime parsedDateTime = handleUserDateTimeInput(enteredValue);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, alterDate + "-confirm", promptKey, dateFormat.format(parsedDateTime), user));
        final String confirmUrl = todoMenus + changeEntry + doSuffix + todoUrlSuffix + todoUid + "&field=" + alterDate
                + "&isoDate=" + DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(parsedDateTime);
        menu.addMenuOptions(optionsYesNo(user, confirmUrl, todoMenus + viewEntryMenu + todoUrlSuffix + todoUid));
        return menuBuilder(menu);
    }

    @RequestMapping(path + changeEntry + doSuffix)
    @ResponseBody
    public Request changeEntryConfirmed(@RequestParam String msisdn, @RequestParam String todoUid,
                                        @RequestParam(value = "field") String field,
                                        @RequestParam(value = userInputParam) String userInput,
                                        @RequestParam(value = "isoDate", required = false) String newActionByDate) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, changeEntry + doSuffix, promptKey, user));
        if (alterSubject.equals(field)) {
            todoBroker.updateSubject(user.getUid(), todoUid, userInput);
            menu.addMenuOption(todoMenus + alterDate + todoUrlSuffix + todoUid, getMessage(thisSection, changeEntry + doSuffix, optionsKey + "date", user));
        } else if (alterDate.equals(field)) {
            todoBroker.updateActionByDate(user.getUid(), todoUid, LocalDateTime.parse(newActionByDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            menu.addMenuOption(todoMenus + alterSubject + todoUrlSuffix + todoUid, getMessage(thisSection, changeEntry + doSuffix, optionsKey + "subject", user));
        } else {
            throw new UnsupportedOperationException("Error! Field must be one of subject or date");
        }
        menu.addMenuOption(todoMenus + viewEntryMenu + todoUrlSuffix + todoUid, getMessage(thisSection, changeEntry + doSuffix, optionsKey + "view", user));
        menu.addMenuOption(todoMenus + startMenu, getMessage(thisSection, changeEntry + doSuffix, optionsKey + "back", user));
        menu.addMenuOption(startMenu + "_force", getMessage(thisSection, changeEntry + doSuffix, optionsKey + "home", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + cancelTodo)
    @ResponseBody
    public Request cancelTodo(@RequestParam String msisdn, @RequestParam String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveToDoMenu(cancelTodo, todoUid));
        Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, cancelTodo, promptKey, todo.getName(), user));
        menu.addMenuOptions(optionsYesNo(user, todoMenus + cancelTodo + doSuffix + todoUrlSuffix + todoUid));
        return menuBuilder(menu);
    }

    @RequestMapping(path + cancelTodo + doSuffix)
    @ResponseBody
    public Request cancelTodoConfirmed(@RequestParam String msisdn, @RequestParam String todoUid,
                                       @RequestParam(value = yesOrNoParam) boolean confirmed) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        if (confirmed) {
            todoBroker.cancel(user.getUid(), todoUid);
            USSDMenu menu = new USSDMenu(getMessage(thisSection, cancelTodo + doSuffix, promptKey, user));
            menu.addMenuOption(todoMenus + startMenu, getMessage(thisSection, cancelTodo + doSuffix, optionsKey + "todos", user));
            return menuBuilder(menu);
        } else {
            return viewEntryMenu(msisdn, todoUid, startMenu);
        }
    }

    private void updateTodoRequest(String userUid, String logBookRequestUid, String field, String value) {
        if (subjectMenu.equals(field)) {
            todoRequestBroker.updateMessage(userUid, logBookRequestUid, value);
        } else if (instantMenu.equals(field)) {
            todoRequestBroker.updateDueDate(userUid, logBookRequestUid, null);
        } else if (dueDateMenu.equals(field)) {
            todoRequestBroker.updateDueDate(userUid, logBookRequestUid, handleUserDateTimeInput(value));
        }
    }

    private LocalDateTime handleUserDateTimeInput(String userInput) {
        String formattedDateString =  reformatDateInput(userInput);
        LocalDateTime dueDateTime; // try process as regex, if fail, hand over to Selo, and if that fails, default to 1 week
        try {
            dueDateTime = convertDateStringToLocalDateTime(formattedDateString, stdHour, stdMinute);
        } catch (IllegalArgumentException e) {
            try {
                dueDateTime = learningService.parse(formattedDateString);
            } catch (SeloParseDateTimeFailure|SeloApiCallFailure t) {
                dueDateTime = LocalDateTime.now().plus(1, ChronoUnit.WEEKS);
            }
        }
        return dueDateTime;
    }

}