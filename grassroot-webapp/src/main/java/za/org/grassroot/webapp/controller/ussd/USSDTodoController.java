package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.integration.LearningService;
import za.org.grassroot.integration.exception.SeloApiCallFailure;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.TodoRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static za.org.grassroot.core.enums.TodoCompletionConfirmType.COMPLETED;
import static za.org.grassroot.core.enums.TodoCompletionConfirmType.NOT_COMPLETED;
import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/12/15.
 */
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDTodoController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDTodoController.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Value("${grassroot.ussd.location.enabled:false}")
    private boolean locationRequestEnabled;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private TodoRequestBroker todoRequestBroker;

    @Autowired
    private LearningService learningService;

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

    private String menuPrompt(String menu, User user) {
        return getMessage(thisSection, menu, promptKey, user);
    }

    private String returnUrl(String nextMenu, String todoUid) {
        return todoMenus + nextMenu + todoUrlSuffix + todoUid;
    }

    private String nextOrConfirmUrl(String thisMenu, String nextMenu, String todoUid, boolean revising) {
        return revising ? returnUrl(confirmMenu, todoUid) + priorMenuSuffix + thisMenu :
                returnUrl(nextMenu, todoUid);
    }

    private String backUrl(String menu, String todoUid) {
        return returnUrl(menu, todoUid) + "&" + revisingFlag + "=1";
    }

    @RequestMapping(path + startMenu)
    @ResponseBody
    public Request askNewOld(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu thisMenu;

        if (permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY) == 0) {
            thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + ".nocreate", user));
        } else {
            thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
            thisMenu.addMenuOption(todoMenus + groupMenu + "?new=true", getMessage(thisSection, startMenu, optionsKey + "new", user));
        }

        if (todoBroker.userHasIncompleteActionsToView(user.getUid())) {
            thisMenu.addMenuOption(todoMenus + incompleteEntries + "?pageNumber=0", getMessage(thisSection, startMenu, optionsKey + incompleteEntries, user));
        }

        if (permissionBroker.countActiveGroupsWithPermission(user, null) != 0) {
            thisMenu.addMenuOption(todoMenus + groupMenu + "?new=false", getMessage(thisSection, startMenu, optionsKey + "old", user));
        }

        thisMenu.addMenuOption(startMenu, getMessage(thisSection,startMenu,optionsKey+"back", user));
        return menuBuilder(thisMenu);
    }

    @RequestMapping(path + groupMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = "new") boolean newEntry) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        int countGroups = permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        if (newEntry) {
            return menuBuilder(initiateNewAction(user, countGroups));
        } else {
            if (countGroups == 1) {
                Group group = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).iterator().next();
                return listCompleteEntriesForGroup(user.getPhoneNumber(), 0, group.getUid());
            } else {
                return menuBuilder(ussdGroupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection)
                        .urlForExistingGroup(listEntriesMenu + "?pageNumber=0")
                        .messageKey("history")
                        .numberOfGroups(countGroups)));
            }
        }
    }

    private USSDMenu initiateNewAction(User user, int groupCount) throws URISyntaxException {
        if (groupCount == 1) {
            Group group = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).iterator().next();
            final String prompt = getMessage(thisSection, subjectMenu, promptKey + ".skipped", group.getName(""), user);
            return setActionGroupAndInitiateRequest(null, group.getUid(), prompt, false, user);
        } else {
            return ussdGroupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection)
                    .urlForExistingGroup(subjectMenu)
                    .messageKey("new")
                    .numberOfGroups(groupCount));
        }
    }

    @RequestMapping(path + subjectMenu)
    @ResponseBody
    public Request askForSubject(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupUidParam, required = false) String groupUid,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = todoUidParam, required = false) String passedTodoUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        final String prompt = getMessage(thisSection, subjectMenu, promptKey, user);
        return menuBuilder(setActionGroupAndInitiateRequest(passedTodoUid, groupUid, prompt, revising, user));
    }

    private USSDMenu setActionGroupAndInitiateRequest(final String passedTodoUid, final String groupUid, final String prompt,
                                                      final boolean revising, User user) {
        try {
            final String todoUid = passedTodoUid != null ? passedTodoUid : todoRequestBroker.create(user.getUid(), groupUid).getUid();
            cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveToDoMenu(subjectMenu, todoUid));
            return new USSDMenu(prompt, nextOrConfirmUrl(subjectMenu, instantMenu, todoUid, revising));
        } catch (AccountLimitExceededException e) {
            final String newPrompt = getMessage(thisSection, "new", promptKey + ".exceeded", user);
            cacheManager.clearUssdMenuForUser(user.getPhoneNumber());
            return new USSDMenu(newPrompt, optionsHomeExit(user, false));
        }
    }

    @RequestMapping(path + instantMenu)
    @ResponseBody
    public Request askIfActionInstant(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = userInputParam) String passedInput,
                                      @RequestParam(value = todoUidParam) String todoUid,
                                      @RequestParam(value = revisingFlag, required = false) boolean revising,
                                      @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                      @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {
        final String userInput = (interrupted) ? priorInput : passedInput;
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(instantMenu, todoUid, userInput));
        if (!revising) todoRequestBroker.updateMessage(user.getUid(), todoUid, userInput);

        USSDMenu menu = new USSDMenu(menuPrompt(instantMenu, user));
        // first option goes to the deadline menu
        menu.addMenuOption(nextOrConfirmUrl(instantMenu, dueDateMenu, todoUid, revising),
                getMessage(thisSection, instantMenu, optionsKey + "no", user));
        // second option marks the action as done and proceeds
        menu.addMenuOption(nextOrConfirmUrl(instantMenu, confirmMenu, todoUid, true),
                getMessage(thisSection, instantMenu, optionsKey + "yes", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + dueDateMenu)
    @ResponseBody
    public Request askForDueDate(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = userInputParam) String passedInput,
                                 @RequestParam(value = todoUidParam) String todoUid,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                 @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        final String userInput = (interrupted) ? priorInput : passedInput;
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(dueDateMenu, todoUid, userInput));
        return menuBuilder(new USSDMenu(menuPrompt(dueDateMenu, user),
                                        nextOrConfirmUrl(dueDateMenu, confirmMenu, todoUid, true)));
    }

    @RequestMapping(path + confirmMenu)
    @ResponseBody
    public Request confirmActionTodo(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = todoUidParam) String todoUid,
                                     @RequestParam(value = userInputParam) String passedUserInput,
                                     @RequestParam(value = previousMenu, required = false) String passedPriorMenu,
                                     @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                     @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        final String userInput = (priorInput !=null) ? priorInput : passedUserInput;
        final String priorMenu = (passedPriorMenu != null) ? passedPriorMenu: "";

        String urlToSave = saveToDoMenu(confirmMenu, todoUid, priorMenu, userInput, !interrupted);

        User user = userManager.findByInputNumber(inputNumber, urlToSave);

        if (!interrupted) {
            updateTodoRequest(user.getUid(), todoUid, priorMenu, userInput);
        }

        TodoRequest todoRequest = todoRequestBroker.load(todoUid);
        Group group = (Group) todoRequest.getParent();

        String prompt;
        boolean isInstant = todoRequest.getActionByDate() == null;
        boolean isInFuture;

        if (!isInstant) {
            isInFuture = todoRequest.getActionByDate().isAfter(Instant.now());
            String formattedDueDate = dateTimeFormat.format(convertToUserTimeZone(todoRequest.getActionByDate(), getSAST()));
            String[] promptFields = new String[]{todoRequest.getMessage(), group.getName(""), formattedDueDate};
            prompt = isInFuture ? getMessage(thisSection, confirmMenu, promptKey + ".unassigned", promptFields, user)
                    : getMessage(thisSection, confirmMenu, promptKey + ".err.past", formattedDueDate, user);
        } else {
            isInFuture = true;
            prompt = getMessage(thisSection, confirmMenu, promptKey + ".instant", new String[]{ todoRequest.getMessage(), group.getName() }, user);
        }

        USSDMenu menu = new USSDMenu(prompt);
        if (isInFuture) {
            menu.addMenuOption(returnUrl(send, todoUid) + "&" + instantMenu + "=" + isInstant,
                    getMessage(thisSection, confirmMenu, optionsKey + "send", user));
        }
        menu.addMenuOption(backUrl(subjectMenu, todoUid), getMessage(thisSection, confirmMenu, optionsKey + "subject", user));
        menu.addMenuOption(backUrl(isInstant ? instantMenu : dueDateMenu, todoUid), getMessage(thisSection, confirmMenu, optionsKey + "duedate", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + send)
    @ResponseBody
    public Request finishTodoEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = todoUidParam) String todoUid,
                                   @RequestParam(value = instantMenu, required = false) boolean isInstant) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        todoRequestBroker.finish(todoUid);
        return menuBuilder(new USSDMenu(menuPrompt(send + (isInstant ? ("." + instantMenu) : ""), user), optionsHomeExit(user, false)));
    }

    /**
     * SECTION: Select and view prior logbook entries
     */

    @RequestMapping(path + incompleteEntries)
    @ResponseBody
    public Request listIncompleteActions(@RequestParam String msisdn,
                                         @RequestParam int pageNumber) throws URISyntaxException {
        final User user = userManager.findByInputNumber(msisdn, todoMenus + incompleteEntries + "?pageNumber=" + pageNumber);
        final Page<Todo> incompleteTodos = todoBroker.fetchIncompleteActionsToView(user.getUid(),
                new PageRequest(pageNumber, PAGE_LENGTH, new Sort(Sort.Direction.DESC, "actionByDate")));
        final String viewUrl = todoMenus + viewEntryMenu + todoUrlSuffix + "%s&back=" + incompleteEntries + "?pageNumber=" + pageNumber;

        USSDMenu menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, promptKey, user));
        incompleteTodos.getContent()
                .forEach(t -> menu.addMenuOption(String.format(viewUrl, t.getUid()), truncateEntryDescription(t, user)));
        if (incompleteTodos.hasNext()) {
            menu.addMenuOption(todoMenus + incompleteEntries + "?pageNumber=" + (pageNumber + 1), getMessage("options.more", user));
        }
        if (incompleteTodos.hasPrevious()) {
            menu.addMenuOption(todoMenus + incompleteEntries + "?pageNumber=" + (pageNumber - 1), getMessage("options.back", user));
        } else {
            menu.addMenuOption(todoMenus + startMenu, getMessage("options.back", user));
        }
        return menuBuilder(menu);
    }

    @RequestMapping(path + listEntriesMenu)
    @ResponseBody
    public Request listCompleteEntriesForGroup(@RequestParam String msisdn,
                                               @RequestParam int pageNumber,
                                               @RequestParam String groupUid) throws URISyntaxException {

        // major todo : in future, adjust menus to strip out groups with nothing to view
        final User user = userManager.findByInputNumber(msisdn, todosViewGroupCompleteEntries(listEntriesMenu, groupUid, pageNumber));
        final Group group = groupBroker.load(groupUid);
        final String urlBase = todoMenus + viewEntryMenu + todoUrlSuffix;
        final String urlSuffix = "&back=" + encodeParameter(listEntriesMenu + "?groupUid=" + groupUid + "&pageNumber=" + pageNumber);

        final Page<Todo> entries = todoBroker.fetchPageOfTodosForGroup(user.getUid(), groupUid,
                new PageRequest(pageNumber, PAGE_LENGTH, new Sort(Sort.Direction.DESC, "actionByDate")));
        // for controlling if option to jump to record todos appears
        boolean canRecordTodo = permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        // for controlling back behaviour
        boolean hasMultipleGroups = permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY) > 1;
        String backUrl = hasMultipleGroups ? todoMenus + groupMenu + "?new=false" : todoMenus + startMenu;

        USSDMenu menu;
        if (!entries.hasContent()) {
            menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, "complete.noentry", group.getName(), user));
            if (canRecordTodo) {
                menu.addMenuOption(todoMenus + subjectMenu + groupUidUrlSuffix + groupUid, getMessage(thisSection, listEntriesMenu, optionsKey + "create", user));
            }
            menu.addMenuOption(backUrl, getMessage(thisSection, listEntriesMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user, false));
        } else {
            menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, promptKey, user));
            entries.forEach(t -> menu.addMenuOption(urlBase + t.getUid() + urlSuffix, truncateEntryDescription(t, user)));
            final String pagingUrl = todoMenus + listEntriesMenu + groupUidUrlSuffix + groupUid + "&pageNumber=";
            if (entries.hasNext()) {
                menu.addMenuOption(pagingUrl + (pageNumber + 1), getMessage("options.more", user));
            }
            if (entries.hasPrevious()) {
                menu.addMenuOption(pagingUrl + (pageNumber - 1), getMessage("options.back", user));
            } else {
                menu.addMenuOption(backUrl, getMessage("options.back", user));
            }
            if (canRecordTodo && menu.getMenuCharLength() < 140)
                menu.addMenuOption(todoMenus + subjectMenu + groupUidUrlSuffix + groupUid, getMessage(thisSection, listEntriesMenu, optionsKey + "create", user));
        }
        return menuBuilder(menu);
    }

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

    @RequestMapping(path + setCompleteMenu)
    @ResponseBody
    public Request setActionTodoAsComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(setCompleteMenu, todoUid));
        Todo todo = todoBroker.load(todoUid);

        final String prompt = todo.getCreatedByUser().equals(user) ? getMessage(thisSection, setCompleteMenu, promptKey + ".creator", todo.getName(), user)
                : todo.isAllGroupMembersAssigned() ? getMessage(thisSection, setCompleteMenu, promptKey + ".unassigned", todo.getName(), user)
                : getMessage(thisSection, setCompleteMenu, promptKey + ".assigned", new String[] { todo.getName(), String.valueOf(todo.getAssignedMembers().size()) }, user);

        USSDMenu menu = new USSDMenu(prompt);

        String urlEnd = todoUrlSuffix + todoUid;
        menu.addMenuOption(todoMenus + setCompleteMenu + doSuffix + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "confirm", user));
        menu.addMenuOption(todoMenus + setCompleteMenu + doSuffix + urlEnd + "&prior_input=" + NOT_COMPLETED.name(),
                getMessage(thisSection, setCompleteMenu, optionsKey + "notsure", user));
        menu.addMenuOption(todoMenus + viewEntryMenu + urlEnd, getMessage("options.back", user));

        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu + doSuffix)
    @ResponseBody
    public Request confirmActionTodoComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam(value = todoUidParam) String todoUid,
                                             @RequestParam(value = "prior_input", required = false) TodoCompletionConfirmType type) throws URISyntaxException {

        TodoCompletionConfirmType confirmType = (type == null) ? COMPLETED : type;
        User user = userManager.findByInputNumber(inputNumber, null);

        boolean crossedThreshold = todoBroker.confirmCompletion(user.getUid(), todoUid, confirmType, LocalDateTime.now());
        Todo todo = todoBroker.load(todoUid);

        final String prompt = crossedThreshold ? getMessage(thisSection, setCompleteMenu, "done.changed", user)
                : getMessage(thisSection, setCompleteMenu, "done.unchanged", String.valueOf((int) todo.getCompletionPercentage()), user);
        USSDMenu menu = new USSDMenu(prompt);
        if (todoBroker.userHasIncompleteActionsToView(user.getUid())) {
            menu.addMenuOption(todoMenus + "incomplete?pageNumber=0", getMessage(thisSection, setCompleteMenu, optionsKey + "listinc", user));
        }
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewAssignment)
    @ResponseBody
    public Request viewTodoAssignments(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(viewAssignment, todoUid));
        Todo todo = todoBroker.load(todoUid);

        List<User> assignedMembers = new ArrayList<>(todo.getAssignedMembers());
        Collections.sort(assignedMembers);
        StringBuilder sb = new StringBuilder(assignedMembers.get(0).nameToDisplay());

        switch(assignedMembers.size()) {
            case 1:
                break;
            case 2:
                sb.append(" and ").append(assignedMembers.get(1).nameToDisplay());
                break;
            case 3:
                sb.append(", ").append(assignedMembers.get(1).nameToDisplay()).append(" and ")
                        .append(assignedMembers.get(2).nameToDisplay());
                break;
            default:
                sb.append(" and ").append(assignedMembers.size() - 1).append(" others"); // todo :i18n for these little phrases
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, viewAssignment, promptKey,
                new String[] { sb.toString(), shortDateFormat.format(todo.getActionByDateAtSAST()) }, user));
        menu.addMenuOption(todoMenus + viewEntryMenu + todoUrlSuffix + todoUid, getMessage(optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user, false));
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

    // major todo: restore this (assignment of users in USSD)
    private USSDMenu pickUserFromGroup(String todoUid, String userInput, String nextMenu, String backMenu, User user) {
        USSDMenu menu;
        Todo todo = todoBroker.load(todoUid);
        Group parent = (Group) todo.getParent();
        List<User> possibleUsers = userManager.searchByGroupAndNameNumber(parent.getUid(), userInput);

        if (!possibleUsers.isEmpty()) {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey, user));
            Iterator<User> iterator = possibleUsers.iterator();
            while (menu.getMenuCharLength() < 100 && iterator.hasNext()) {
                User possibleUser = iterator.next();
                menu.addMenuOption(returnUrl(nextMenu, todoUid) + "&assignUserUid=" + possibleUser.getUid(),
                        possibleUser.nameToDisplay());
            }
        } else {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey + ".no-users", user));
        }

        menu.addMenuOption(returnUrl(backMenu, todoUid), getMessage(thisSection, pickUserMenu, optionsKey + "back", user));
        menu.addMenuOption(returnUrl(nextMenu, todoUid), getMessage(thisSection, pickUserMenu, optionsKey + "none", user));

        return menu;
    }
}