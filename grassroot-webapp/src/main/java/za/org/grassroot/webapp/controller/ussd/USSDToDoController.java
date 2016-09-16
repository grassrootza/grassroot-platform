package za.org.grassroot.webapp.controller.ussd;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.domain.SeloApiCallFailure;
import za.org.grassroot.integration.domain.SeloParseDateTimeFailure;
import za.org.grassroot.integration.services.LearningService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.TodoBroker;
import za.org.grassroot.services.TodoRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.encodeParameter;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveToDoMenu;

/**
 * Created by luke on 2015/12/15.
 */
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDToDoController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDToDoController.class);

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
            dueDateMenu = "due_date",
            searchUserMenu = "search_user",
            pickUserMenu = "pick_user",
            confirmMenu = "confirm",
            send = "send";

    private static final String listEntriesMenu = "list",
            viewEntryMenu = "view",
            viewEntryDates = "view_dates",
            viewAssignment = "view_assigned",
            setCompleteMenu = "set_complete",
            completingUser = "choose_completor",
            pickCompletor = "pick_completor",
            completedDate = "date_completed",
            confirmCompleteDate = "confirm_date";

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
        if (permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).isEmpty()) {
            thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + ".nocreate", user));
        } else {
            thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
            thisMenu.addMenuOption(todoMenus + groupMenu + "?new=true", getMessage(thisSection, startMenu, optionsKey + "new", user));
        }

        thisMenu.addMenuOption(todoMenus + listEntriesMenu + "?done=false", getMessage(thisSection, startMenu, optionsKey + "incomplete", user));

        thisMenu.addMenuOption(todoMenus + groupMenu + "?new=false&completed=true", getMessage(thisSection, startMenu, optionsKey + "old", user));
        thisMenu.addMenuOption(startMenu, getMessage(thisSection,startMenu,optionsKey+"back", user));
        return menuBuilder(thisMenu);
    }

    @RequestMapping(path + groupMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = "new") boolean newEntry,
                             @RequestParam(value = "completed", required=false) boolean completed) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        // todo : handle conflicting prompts etc in here (actually, up above)
        if (newEntry) {
            return menuBuilder(initiateNewAction(user));
        } else {
            int countGroups = permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
            if (countGroups == 1) {
                Group group = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).iterator().next();
                return listEntriesMenu(user.getPhoneNumber(), group.getUid(), completed, 0);
            } else {
                return menuBuilder(ussdGroupUtil.askForGroup(user, thisSection, listEntriesMenu + "?done=" + completed, null, null, null, countGroups));
            }
        }
    }

    private USSDMenu initiateNewAction(User user) throws URISyntaxException {
        int numberPossibleGroups = permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY); // todo : consolidate counts
        if (numberPossibleGroups == 1) {
            Group group = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).iterator().next();
            final String prompt = getMessage(thisSection, subjectMenu, promptKey + ".skipped", group.getName(""), user);
            return setActionGroupAndInitiateRequest(null, group.getUid(), prompt, false, user);
        } else {
            return ussdGroupUtil.askForGroup(user, thisSection, subjectMenu, null, null, null, numberPossibleGroups);
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
        final String todoUid = passedTodoUid != null ? passedTodoUid : todoRequestBroker.create(user.getUid(), groupUid).getUid();
        cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveToDoMenu(subjectMenu, todoUid));
        return new USSDMenu(prompt, nextOrConfirmUrl(subjectMenu, dueDateMenu, todoUid, revising));
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
        if (!revising) todoRequestBroker.updateMessage(user.getUid(), todoUid, userInput);
        return menuBuilder(new USSDMenu(menuPrompt(dueDateMenu, user),
                                        nextOrConfirmUrl(dueDateMenu, confirmMenu, todoUid, true)));
    }

    @RequestMapping(path + confirmMenu)
    @ResponseBody
    public Request confirmLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = todoUidParam) String todoUid,
                                       @RequestParam(value = userInputParam) String passedUserInput,
                                       @RequestParam(value = previousMenu, required = false) String passedPriorMenu,
                                       @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                       @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        final String userInput = (priorInput !=null) ? priorInput : passedUserInput;
        final String priorMenu = (passedPriorMenu != null) ? passedPriorMenu: "";

        String urlToSave = saveToDoMenu(confirmMenu, todoUid, priorMenu, userInput, !interrupted);

        User user = userManager.findByInputNumber(inputNumber, urlToSave);

        if (!interrupted) updateLogBookRequest(user.getUid(), todoUid, priorMenu, userInput);
        TodoRequest todoRequest = todoRequestBroker.load(todoUid);

        boolean isInFuture = todoRequest.getActionByDate().isAfter(Instant.now());

        String formattedDueDate = dateFormat.format(convertToUserTimeZone(todoRequest.getActionByDate(), getSAST()));

        Group group = (Group) todoRequest.getParent();
        String[] promptFields = new String[]{todoRequest.getMessage(), group.getName(""),
                formattedDueDate};

        final String prompt = isInFuture ? getMessage(thisSection, confirmMenu, promptKey + ".unassigned", promptFields, user)
                : getMessage(thisSection, confirmMenu, promptKey + ".err.past", formattedDueDate, user);

        USSDMenu menu = new USSDMenu(prompt);
        if (isInFuture) {
            menu.addMenuOption(returnUrl(send, todoUid), getMessage(thisSection, confirmMenu, optionsKey + "send", user));
        }
        menu.addMenuOption(backUrl(subjectMenu, todoUid), getMessage(thisSection, confirmMenu, optionsKey + "subject", user));
        menu.addMenuOption(backUrl(dueDateMenu, todoUid), getMessage(thisSection, confirmMenu, optionsKey + "duedate", user));
        // menu.addMenuOption(backUrl(assignMenu, todoUid), getMessage(thisSection, confirmMenu, optionsKey + "assign", user));

        return menuBuilder(menu);
    }

    @RequestMapping(path + send)
    @ResponseBody
    public Request finishLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        todoRequestBroker.finish(todoUid);
        return menuBuilder(new USSDMenu(menuPrompt(send, user), optionsHomeExit(user)));
    }

    /**
     * SECTION: Select and view prior logbook entries
     */

    @RequestMapping(path + listEntriesMenu)
    @ResponseBody
    public Request listEntriesMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam, required= false) String groupUid,
                                   @RequestParam(value = "done") boolean done,
                                   @RequestParam(value = "pageNumber", required = false) Integer passedPageNumber) throws URISyntaxException {


        final int pageNumber = (passedPageNumber == null) ? 0 : passedPageNumber;
        final User user = userManager.findByInputNumber(inputNumber, USSDUrlUtil.logViewExistingUrl(listEntriesMenu, groupUid, done, pageNumber));

        final String urlBase = todoMenus + viewEntryMenu + todoUrlSuffix;
        final Page<Todo> entries = todoBroker.fetchPageOfTodosForGroup(user.getUid(), groupUid, done, pageNumber, PAGE_LENGTH);

        boolean canCreateToDos = permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY) != 0;
        boolean hasMultipleGroups = permissionBroker.countActiveGroupsWithPermission(user, null) > 1;

        String backUrl = (groupUid == null) ? todoMenus + startMenu :
                (hasMultipleGroups ? todoMenus + groupMenu + "?new=false&completed=" + done : todoMenus + startMenu);

        USSDMenu menu;
        if (!entries.hasContent()) {
            menu = done ? new USSDMenu(getMessage(thisSection, listEntriesMenu, "complete.noentry", user)) :
                    new USSDMenu(getMessage(thisSection, listEntriesMenu, "incomplete.noentry", user));
            if (canCreateToDos)
                menu.addMenuOption(todoMenus + groupMenu + "?new=true", getMessage(thisSection, listEntriesMenu, optionsKey + "create", user));
            menu.addMenuOption(backUrl, getMessage(thisSection, listEntriesMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, promptKey, user));
            for (Todo entry : entries) {
                String description = truncateEntryDescription(entry);
                menu.addMenuOption(urlBase + entry.getUid(), description);
            }
            if (entries.hasNext()) {
                String nextPageUri = todoMenus + listEntriesMenu + groupUidUrlSuffix + groupUid + "&done=" + done + "&pageNumber=" + (pageNumber + 1);
                menu.addMenuOption(nextPageUri, getMessage(thisSection, listEntriesMenu, "more", user));
            }
            if (entries.hasPrevious()) {
                String previousPageUri = todoMenus + listEntriesMenu + groupUidUrlSuffix + groupUid + "&done=" + done + "&pageNumber=" + (pageNumber - 1);
                menu.addMenuOption(previousPageUri, getMessage(thisSection, listEntriesMenu, "previous", user));
            } else {
                if (canCreateToDos)
                    menu.addMenuOption(todoMenus + groupMenu + "?new=true", getMessage(thisSection, listEntriesMenu, optionsKey + "create", user));
                menu.addMenuOption(backUrl, getMessage(thisSection, listEntriesMenu, optionsKey + "back", user));
            }
        }
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewEntryMenu)
    @ResponseBody
    public Request viewEntryMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        final User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(viewEntryMenu, todoUid));
        final Todo todo = todoBroker.load(todoUid);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, viewEntryMenu, promptKey, todo.getMessage(), user));

        // todo: check permissions before deciding what options to display
        menu.addMenuOption(returnUrl(viewEntryDates, todoUid), getMessage(thisSection, viewEntryMenu, optionsKey + "dates", user));

        if (todo.isCompleted()) {
            menu.addMenuOption(returnUrl(viewAssignment, todoUid),
                    getMessage(thisSection, viewEntryMenu, optionsKey + "viewcomplete", user));
        } else {
            if (todo.isAllGroupMembersAssigned()) {
                menu.addMenuOption(returnUrl(setCompleteMenu, todoUid), getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user));
            } else if (todo.getAssignedMembers().contains(user)) {
                menu.addMenuOption(returnUrl(setCompleteMenu, todoUid), getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user));
            } else {
                menu.addMenuOption(returnUrl(viewAssignment, todoUid), getMessage(thisSection, viewEntryMenu, optionsKey + "assigned", user));
            }
        }

        menu.addMenuOption("exit", "Exit");
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewEntryDates)
    @ResponseBody
    public Request viewLogBookDates(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        Todo todo = todoBroker.load(todoUid);
        String createdDate = dateFormat.format(convertToUserTimeZone(todo.getCreatedDateTime(), getSAST()));
        String dueDate = dateFormat.format(convertToUserTimeZone(todo.getActionByDate(), getSAST()));

        USSDMenu menu;
        if (todo.isCompleted()) {
            String completedDate = dateFormat.format(convertToUserTimeZone(todo.getCompletedDate(), getSAST()));
            // todo: accomodate to new design without single completion user
//            String userCompleted = todo.getCompletedByUser() == null ? "" : "by " + todo.getCompletedByUser().nameToDisplay();
            String userCompleted = "by <UNKNOWN>";
            String[] fields = new String[]{createdDate, dueDate, completedDate, userCompleted};
            menu = new USSDMenu(getMessage(thisSection, viewEntryDates, promptKey + ".complete", fields, user));
        } else {
            String[] fields = new String[]{createdDate, dueDate};
            menu = new USSDMenu(getMessage(thisSection, viewEntryDates, promptKey + ".incomplete", fields, user));
        }

        menu.addMenuOption(todoMenus + viewEntryMenu + todoUrlSuffix + todoUid, getMessage(optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewAssignment)
    @ResponseBody
    public Request viewLogBookAssignment(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        Todo todo = todoBroker.load(todoUid);

        USSDMenu menu;

        String assignedFragment, completedFragment;

        if (todo.isCompleted()) {
            completedFragment = getMessage(thisSection, viewAssignment, "complete",
                                           dateFormat.format(convertToUserTimeZone(todo.getCompletedDate(), getSAST())), user);
            assignedFragment = "";
        } else {
            completedFragment = getMessage(thisSection, viewAssignment, "incomplete",
                                           dateFormat.format(convertToUserTimeZone(todo.getActionByDate(), getSAST())), user);
            if (todo.isAllGroupMembersAssigned()) {
                assignedFragment = getMessage(thisSection, viewAssignment, "group", user);
            } else {
                Set<String> assignedMemberNames = todo.getAssignedMembers().stream().map(u -> u.nameToDisplay()).collect(Collectors.toSet());
                assignedFragment = Joiner.on(", ").join(assignedMemberNames);
            }
        }

        menu = new USSDMenu(getMessage(thisSection, viewAssignment, promptKey,
                new String[]{assignedFragment, completedFragment}, user));

        menu.addMenuOption(todoMenus + viewEntryMenu + todoUrlSuffix + todoUid, getMessage(optionsKey + "back", user));
        if (!todo.isCompleted()) menu.addMenuOption(todoMenus + setCompleteMenu + todoUrlSuffix + todoUid,
                getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user)); // todo: check permissions
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu)
    @ResponseBody
    public Request setLogBookEntryComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {

        // todo: check permissions
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(setCompleteMenu, todoUid));

        // note: can pick completing user via USSD, though can't do multi-assignment
        USSDMenu menu = new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey + ".unassigned", user));

        String urlEnd = todoUrlSuffix + todoUid;
        menu.addMenuOption(todoMenus + setCompleteMenu + doSuffix + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "confirm", user));
        menu.addMenuOption(todoMenus + completingUser + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "assign", user));
        menu.addMenuOption(todoMenus + completedDate + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "date", user));
        menu.addMenuOption(todoMenus + viewEntryMenu + urlEnd, getMessage(optionsKey + "back", user));

        return menuBuilder(menu);
    }

    @RequestMapping(path + completingUser)
    @ResponseBody
    public Request selectCompletingUser(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(completingUser, todoUid));
        USSDMenu menu = new USSDMenu(menuPrompt(searchUserMenu, user), returnUrl(pickCompletor, todoUid));
        return menuBuilder(menu);
    }

    @RequestMapping(path + pickCompletor)
    @ResponseBody
    public Request pickCompletor(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = todoUidParam) String todoUid,
                                 @RequestParam(value = userInputParam) String passedInput,
                                 @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                 @RequestParam(value = interruptedInput, required =false) String prior_input) throws URISyntaxException {
        final String userInput = interrupted ? prior_input : passedInput;
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(pickCompletor, todoUid, userInput));
        return menuBuilder(pickUserFromGroup(todoUid, userInput, setCompleteMenu + doSuffix, completingUser, user));
    }

    @RequestMapping(path + completedDate)
    @ResponseBody
    public Request enterCompletedDate(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = todoUidParam) String todoUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(completedDate, todoUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, completedDate, promptKey, user),
                returnUrl(confirmCompleteDate, todoUid)));
    }

    @RequestMapping(path + confirmCompleteDate)
    @ResponseBody
    public Request confirmCompletedDate(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = todoUidParam) String todoUid,
                                        @RequestParam(value = userInputParam) String passedUserInput,
                                        @RequestParam(value = interruptedFlag,required=false) boolean interrupted,
                                        @RequestParam(value = interruptedInput, required =false) String priorInput) throws URISyntaxException {

        final String userInput = (priorInput != null) ? priorInput : passedUserInput;
        log.info("ZOG: Going to save this menu ... " + saveToDoMenu(confirmCompleteDate, todoUid, userInput, !interrupted));
        User user = userManager.findByInputNumber(inputNumber, saveToDoMenu(confirmCompleteDate, todoUid, userInput, !interrupted));
        String formattedResponse = reformatDateInput(userInput);
        String confirmUrl = returnUrl(setCompleteMenu + doSuffix, todoUid) + "&completed_date=" + encodeParameter(formattedResponse);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, confirmCompleteDate, promptKey, formattedResponse, user));
        menu.addMenuOption(confirmUrl, getMessage(thisSection, confirmCompleteDate, optionsKey + "yes", formattedResponse, user));
        menu.addMenuOption(returnUrl(completedDate, todoUid), getMessage(thisSection, confirmCompleteDate, optionsKey + "no", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu + doSuffix)
    @ResponseBody
    public Request setLogBookEntryComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = todoUidParam) String todoUid,
                                       @RequestParam(value = "completed_date", required = false) String completedDate) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);

        LocalDateTime completedDateTime = (completedDate != null) ?
                convertDateStringToLocalDateTime(reformatDateInput(completedDate), stdHour, stdMinute) : LocalDateTime.now();
        userLogger.recordUserInputtedDateTime(user.getUid(), completedDate, "logbook-completion", UserInterfaceType.USSD);
        todoBroker.confirmCompletion(user.getUid(), todoUid, TodoCompletionConfirmType.COMPLETED, completedDateTime);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey, user));
        // todo: consider adding option to go back to either section start or group logbook start
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    private void updateLogBookRequest(String userUid, String logBookRequestUid, String field, String value) {
        switch (field) {
            case subjectMenu:
                todoRequestBroker.updateMessage(userUid, logBookRequestUid, value);
                break;
            case dueDateMenu:
                String formattedDateString =  reformatDateInput(value);
                LocalDateTime dueDateTime;
                // todo : clean this up
                try {
                    dueDateTime = convertDateStringToLocalDateTime(formattedDateString, stdHour, stdMinute);
                } catch (Exception e) {
                    try {
                        dueDateTime = learningService.parse(formattedDateString);
                    } catch (SeloParseDateTimeFailure|SeloApiCallFailure t) {
                        dueDateTime = LocalDateTime.now().plus(1, ChronoUnit.WEEKS);
                    }
                }
                todoRequestBroker.updateDueDate(userUid, logBookRequestUid, dueDateTime);
                break;
            default:
                throw new UnsupportedOperationException("Error! Request field must be due date or subject");
        }
    }

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

    private String truncateEntryDescription(Todo entry) {
        StringBuilder stringBuilder = new StringBuilder();
        Pattern pattern = Pattern.compile(" ");
        int maxLength = 30;
        for (String word : pattern.split(entry.getMessage())) {
            if ((stringBuilder.length() + word.length() + 1) > maxLength) {
                break;
            } else
                stringBuilder.append(word).append(" ");
        }
        return stringBuilder.toString();
    }
}