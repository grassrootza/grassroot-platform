package za.org.grassroot.webapp.controller.ussd;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.LogBookRequestBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToUserTimeZone;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;
import static za.org.grassroot.core.util.DateTimeUtil.reformatDateInput;
import static za.org.grassroot.webapp.util.USSDUrlUtil.encodeParameter;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveLogMenu;

/**
 * Created by luke on 2015/12/15.
 */
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDLogBookController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDLogBookController.class);

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private LogBookBroker logBookBroker;

    @Autowired
    private LogBookRequestBroker logBookRequestBroker;

    private static final USSDSection thisSection = USSDSection.LOGBOOK;
    private static final String path = homePath + logMenus;

    private static final String groupMenu = "group",
            subjectMenu = "subject",
            dueDateMenu = "due_date",
            assignMenu = "assign",
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
            confirmCompleteDate = "confirm_date",
            assignUserID = "assignUserUid";

    private static final String logBookParam = "logbookUid", logBookUrlSuffix = "?" + logBookParam + "=";
    private static final String priorMenuSuffix = "&" + previousMenu + "=";

    private static final int PAGE_LENGTH = 3;
    private static final int stdHour =13, stdMinute =00;

    private String menuPrompt(String menu, User user) {
        return getMessage(thisSection, menu, promptKey, user);
    }

    private String returnUrl(String nextMenu, String logBookUid) {
        return logMenus + nextMenu + logBookUrlSuffix + logBookUid;
    }

    private String nextOrConfirmUrl(String thisMenu, String nextMenu, String logBookUid, boolean revising) {
        return revising ? returnUrl(confirmMenu, logBookUid) + priorMenuSuffix + thisMenu :
                returnUrl(nextMenu, logBookUid);
    }

    private String backUrl(String menu, String logBookUid) {
        return returnUrl(menu, logBookUid) + "&" + revisingFlag + "=1";
    }


    @RequestMapping(path + startMenu)
    @ResponseBody
    public Request askNewOld(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu thisMenu;
        if (permissionBroker.getActiveGroups(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).isEmpty()) {
            thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + ".nocreate", user));
        } else {
            thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
            thisMenu.addMenuOption(logMenus + groupMenu + "?new=true", getMessage(thisSection, startMenu, optionsKey + "new", user));
        }
        thisMenu.addMenuOption(logMenus + listEntriesMenu + "?done=false", getMessage(thisSection, startMenu, optionsKey + "incomplete", user));
        thisMenu.addMenuOption(logMenus + groupMenu + "?new=false&completed=true", getMessage(thisSection, startMenu, optionsKey + "old", user));
        return menuBuilder(thisMenu);
    }

    @RequestMapping(path + groupMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = "new") boolean newEntry,
                             @RequestParam(value = "completed", required=false) boolean completed) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);

        if (newEntry) {
            return menuBuilder(ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, subjectMenu));
        } else {
            List<GroupDTO> groups = permissionBroker.getActiveGroupDTOs(user, null);
            if (groups.size() == 1) {
                return listEntriesMenu(user.getPhoneNumber(), groups.get(0).getUid(), completed, 0);
            } else {
                return menuBuilder(ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, listEntriesMenu + "?done=" + completed,
                                                                getMessage(thisSection, groupMenu, promptKey + ".existing", user)));
            }
        }
    }

    @RequestMapping(path + subjectMenu)
    @ResponseBody
    public Request askForSubject(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupUidParam, required = false) String groupUid,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = logBookParam, required = false) String logBookUid,
                                 @RequestParam(value = interruptedFlag, required=false) boolean interrupted) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);

        if (logBookUid == null) {
            logBookUid = logBookRequestBroker.create(user.getUid(), groupUid).getUid();
        }

        cacheManager.putUssdMenuForUser(inputNumber, saveLogMenu(subjectMenu, logBookUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, subjectMenu, promptKey, user),
                                     nextOrConfirmUrl(subjectMenu, dueDateMenu, logBookUid, revising));
        return menuBuilder(menu);
    }

    @RequestMapping(path + dueDateMenu)
    @ResponseBody
    public Request askForDueDate(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = userInputParam) String userInput,
                                 @RequestParam(value = logBookParam) String logBookUid,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                 @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        userInput = (interrupted) ? priorInput : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(dueDateMenu, logBookUid, userInput));
        if (!revising) logBookRequestBroker.updateMessage(user.getUid(), logBookUid, userInput);
        return menuBuilder(new USSDMenu(menuPrompt(dueDateMenu, user),
                                        nextOrConfirmUrl(dueDateMenu, confirmMenu, logBookUid, true)));
    }

    @RequestMapping(path + confirmMenu)
    @ResponseBody
    public Request confirmLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = logBookParam) String logBookUid,
                                       @RequestParam(value = userInputParam) String userInput,
                                       @RequestParam(value = previousMenu, required = false) String priorMenu,
                                       @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                       @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        userInput = (priorInput !=null) ? priorInput : userInput;
        priorMenu = (priorMenu != null) ? priorMenu: "";
        String urlToSave = saveLogMenu(confirmMenu, logBookUid, priorMenu, userInput, !interrupted);

        User user = userManager.findByInputNumber(inputNumber, urlToSave);

        if (!interrupted) updateLogBookRequest(user.getUid(), logBookUid, priorMenu, userInput);
        LogBookRequest logBookRequest = logBookRequestBroker.load(logBookUid);

        String formattedDueDate = dateFormat.format(convertToUserTimeZone(logBookRequest.getActionByDate(), getSAST()));

        Group group = (Group) logBookRequest.getParent();
        String[] promptFields = new String[]{logBookRequest.getMessage(), group.getName(""),
                formattedDueDate};

        USSDMenu menu = new USSDMenu(getMessage(thisSection, confirmMenu, promptKey + ".unassigned", promptFields, user));
        menu.addMenuOption(returnUrl(send, logBookUid), getMessage(thisSection, confirmMenu, optionsKey + "send", user));
        menu.addMenuOption(backUrl(subjectMenu, logBookUid), getMessage(thisSection, confirmMenu, optionsKey + "subject", user));
        menu.addMenuOption(backUrl(dueDateMenu, logBookUid), getMessage(thisSection, confirmMenu, optionsKey + "duedate", user));
        // menu.addMenuOption(backUrl(assignMenu, logBookUid), getMessage(thisSection, confirmMenu, optionsKey + "assign", user));

        return menuBuilder(menu);
    }

    @RequestMapping(path + send)
    @ResponseBody
    public Request finishLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        logBookRequestBroker.finish(logBookUid);
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
                                   @RequestParam(value = "pageNumber", required = false) Integer pageNumber) throws URISyntaxException {


        pageNumber = (pageNumber == null) ? 0 : pageNumber;
        User user = userManager.findByInputNumber(inputNumber,
                USSDUrlUtil.logViewExistingUrl(listEntriesMenu, groupUid, done, pageNumber));

        String urlBase = logMenus + viewEntryMenu + logBookUrlSuffix;
        Page<LogBook> entries = logBookBroker.retrieveGroupLogBooks(user.getUid(), groupUid, done, pageNumber, PAGE_LENGTH);
        boolean canCreateToDos = permissionBroker.getActiveGroupDTOs(user, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY).isEmpty();
        boolean hasMultipleGroups = permissionBroker.getActiveGroupDTOs(user, null).size() > 1;

        String backUrl = (groupUid == null) ? logMenus + startMenu :
                (hasMultipleGroups ? logMenus + groupMenu + "?new=false&completed=" + done : logMenus + startMenu);

        USSDMenu menu;
        if (!entries.hasContent()) {
            menu = done ? new USSDMenu(getMessage(thisSection, listEntriesMenu, "complete.noentry", user)) :
                    new USSDMenu(getMessage(thisSection, listEntriesMenu, "incomplete.noentry", user));
            if (canCreateToDos)
                menu.addMenuOption(logMenus + groupMenu + "?new=true", getMessage(thisSection, listEntriesMenu, optionsKey + "create", user));
            menu.addMenuOption(backUrl, getMessage(thisSection, listEntriesMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user));
        } else {
            menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, promptKey, user));
            for (LogBook entry : entries) {
                String description = truncateEntryDescription(entry);
                menu.addMenuOption(urlBase + entry.getUid(), description);
            }
            if (entries.hasNext()) {
                String nextPageUri = logMenus + listEntriesMenu + groupUidUrlSuffix + groupUid + "&done=" + done + "&pageNumber=" + (pageNumber + 1);
                menu.addMenuOption(nextPageUri, getMessage(thisSection, listEntriesMenu, "more", user));
            }
            if (entries.hasPrevious()) {
                String previousPageUri = logMenus + listEntriesMenu + groupUidUrlSuffix + groupUid + "&done=" + done + "&pageNumber=" + (pageNumber - 1);
                menu.addMenuOption(previousPageUri, getMessage(thisSection, listEntriesMenu, "previous", user));
            } else {
                if (canCreateToDos)
                    menu.addMenuOption(logMenus + groupMenu + "?new=true", getMessage(thisSection, listEntriesMenu, optionsKey + "create", user));
                menu.addMenuOption(backUrl, getMessage(thisSection, listEntriesMenu, optionsKey + "back", user));
            }
        }
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewEntryMenu)
    @ResponseBody
    public Request viewEntryMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(viewEntryMenu, logBookUid));
        LogBook logBook = logBookBroker.load(logBookUid);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, viewEntryMenu, promptKey, logBook.getMessage(), user));

        // todo: check permissions before deciding what options to display
        menu.addMenuOption(returnUrl(viewEntryDates, logBookUid), getMessage(thisSection, viewEntryMenu, optionsKey + "dates", user));

        if (logBook.isCompleted()) {
            menu.addMenuOption(returnUrl(viewAssignment, logBookUid),
                    getMessage(thisSection, viewEntryMenu, optionsKey + "viewcomplete", user));
        } else {
            if (logBook.isAllGroupMembersAssigned()) {
                menu.addMenuOption(returnUrl(setCompleteMenu, logBookUid), getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user));
            } else if (logBook.getAssignedMembers().contains(user)) {
                menu.addMenuOption(returnUrl(setCompleteMenu, logBookUid), getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user));
            } else {
                menu.addMenuOption(returnUrl(viewAssignment, logBookUid), getMessage(thisSection, viewEntryMenu, optionsKey + "assigned", user));
            }
        }

        menu.addMenuOption("exit", "Exit");
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewEntryDates)
    @ResponseBody
    public Request viewLogBookDates(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        LogBook logBook = logBookBroker.load(logBookUid);
        String createdDate = dateFormat.format(convertToUserTimeZone(logBook.getCreatedDateTime(), getSAST()));
        String dueDate = dateFormat.format(convertToUserTimeZone(logBook.getActionByDate(), getSAST()));

        USSDMenu menu;
        if (logBook.isCompleted()) {
            String completedDate = dateFormat.format(convertToUserTimeZone(logBook.getCompletedDate(), getSAST()));
            // todo: accomodate to new design without single completion user
//            String userCompleted = logBook.getCompletedByUser() == null ? "" : "by " + logBook.getCompletedByUser().nameToDisplay();
            String userCompleted = "by <UNKNOWN>";
            String[] fields = new String[]{createdDate, dueDate, completedDate, userCompleted};
            menu = new USSDMenu(getMessage(thisSection, viewEntryDates, promptKey + ".complete", fields, user));
        } else {
            String[] fields = new String[]{createdDate, dueDate};
            menu = new USSDMenu(getMessage(thisSection, viewEntryDates, promptKey + ".incomplete", fields, user));
        }

        menu.addMenuOption(logMenus + viewEntryMenu + logBookUrlSuffix + logBookUid, getMessage(optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewAssignment)
    @ResponseBody
    public Request viewLogBookAssignment(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        LogBook logBook = logBookBroker.load(logBookUid);

        USSDMenu menu;

        String assignedFragment, completedFragment;

        if (logBook.isCompleted()) {
            completedFragment = getMessage(thisSection, viewAssignment, "complete",
                                           dateFormat.format(convertToUserTimeZone(logBook.getCompletedDate(), getSAST())), user);
            assignedFragment = "";
        } else {
            completedFragment = getMessage(thisSection, viewAssignment, "incomplete",
                                           dateFormat.format(convertToUserTimeZone(logBook.getActionByDate(), getSAST())), user);
            if (logBook.isAllGroupMembersAssigned()) {
                assignedFragment = getMessage(thisSection, viewAssignment, "group", user);
            } else {
                Set<String> assignedMemberNames = logBook.getAssignedMembers().stream().map(u -> u.nameToDisplay()).collect(Collectors.toSet());
                assignedFragment = Joiner.on(", ").join(assignedMemberNames);
            }
        }

        menu = new USSDMenu(getMessage(thisSection, viewAssignment, promptKey,
                new String[]{assignedFragment, completedFragment}, user));

        menu.addMenuOption(logMenus + viewEntryMenu + logBookUrlSuffix + logBookUid, getMessage(optionsKey + "back", user));
        if (!logBook.isCompleted()) menu.addMenuOption(logMenus + setCompleteMenu + logBookUrlSuffix + logBookUid,
                getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user)); // todo: check permissions
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu)
    @ResponseBody
    public Request setLogBookEntryComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {

        // todo: check permissions
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(setCompleteMenu, logBookUid));

        // note: can pick completing user via USSD, though can't do multi-assignment
        USSDMenu menu = new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey + ".unassigned", user));

        String urlEnd = logBookUrlSuffix + logBookUid;
        menu.addMenuOption(logMenus + setCompleteMenu + doSuffix + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "confirm", user));
        menu.addMenuOption(logMenus + completingUser + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "assign", user));
        menu.addMenuOption(logMenus + completedDate + urlEnd,
                getMessage(thisSection, setCompleteMenu, optionsKey + "date", user));
        menu.addMenuOption(logMenus + viewEntryMenu + urlEnd, getMessage(optionsKey + "back", user));

        return menuBuilder(menu);
    }

    @RequestMapping(path + completingUser)
    @ResponseBody
    public Request selectCompletingUser(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(completingUser, logBookUid));
        USSDMenu menu = new USSDMenu(menuPrompt(searchUserMenu, user), returnUrl(pickCompletor, logBookUid));
        return menuBuilder(menu);
    }

    @RequestMapping(path + pickCompletor)
    @ResponseBody
    public Request pickCompletor(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = logBookParam) String logBookUid,
                                 @RequestParam(value = userInputParam) String userInput,
                                 @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                 @RequestParam(value = interruptedInput, required =false) String prior_input) throws URISyntaxException {
        userInput = interrupted ? prior_input : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(pickCompletor, logBookUid, userInput));
        return menuBuilder(pickUserFromGroup(logBookUid, userInput, setCompleteMenu + doSuffix, completingUser, user));
    }

    @RequestMapping(path + completedDate)
    @ResponseBody
    public Request enterCompletedDate(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) String logBookUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(completedDate, logBookUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, completedDate, promptKey, user),
                returnUrl(confirmCompleteDate, logBookUid)));
    }

    @RequestMapping(path + confirmCompleteDate)
    @ResponseBody
    public Request confirmCompletedDate(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = logBookParam) String logBookUid,
                                        @RequestParam(value = userInputParam) String userInput,
                                        @RequestParam(value = interruptedFlag,required=false) boolean interrupted,
                                        @RequestParam(value = interruptedInput, required =false) String priorInput) throws URISyntaxException {

        userInput = (priorInput !=null) ? priorInput : userInput;
        log.info("ZOG: Going to save this menu ... " + saveLogMenu(confirmCompleteDate, logBookUid, userInput, !interrupted));
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(confirmCompleteDate, logBookUid, userInput, !interrupted));
        String formattedResponse = reformatDateInput(userInput);
        String confirmUrl = returnUrl(setCompleteMenu + doSuffix, logBookUid) + "&completed_date=" + encodeParameter(formattedResponse);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, confirmCompleteDate, promptKey, formattedResponse, user));
        menu.addMenuOption(confirmUrl, getMessage(thisSection, confirmCompleteDate, optionsKey + "yes", formattedResponse, user));
        menu.addMenuOption(returnUrl(completedDate, logBookUid), getMessage(thisSection, confirmCompleteDate, optionsKey + "no", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu + doSuffix)
    @ResponseBody
    public Request setLogBookEntryComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = logBookParam) String logBookUid,
                                       @RequestParam(value = assignUserID, required = false) String completedByUserUid,
                                       @RequestParam(value = "completed_date", required = false) String completedDate) throws URISyntaxException {
        // todo: check permissions
        User user = userManager.findByInputNumber(inputNumber, null);

        LocalDateTime completedDateTime = (completedDate != null) ?
                DateTimeUtil.convertDateStringToLocalDateTime(reformatDateInput(completedDate), stdHour, stdMinute) : LocalDateTime.now();
        userLogger.recordUserInputtedDateTime(user.getUid(), completedDate, "logbook-completion", UserInterfaceType.USSD);
        logBookBroker.confirmCompletion(completedByUserUid, logBookUid, completedDateTime);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey, user));
        // todo: consider adding option to go back to either section start or group logbook start
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    private void updateLogBookRequest(String userUid, String logBookRequestUid, String field, String value) {
        switch (field) {
            case subjectMenu:
                logBookRequestBroker.updateMessage(userUid, logBookRequestUid, value);
                break;
            case dueDateMenu:
                String formattedDateString =  reformatDateInput(value);
                LocalDateTime dueDateTime;
                try {
                    dueDateTime = DateTimeUtil.convertDateStringToLocalDateTime(formattedDateString, stdHour, stdMinute);
                } catch (Exception e) {
                    dueDateTime = USSDEventUtil.parseDateTime(formattedDateString);
                }
                logBookRequestBroker.updateDueDate(userUid, logBookRequestUid, dueDateTime);
                break;
        }
    }

    private USSDMenu pickUserFromGroup(String logBookUid, String userInput, String nextMenu, String backMenu, User user) {

        USSDMenu menu;
        LogBook logBook = logBookBroker.load(logBookUid);
        Group parent = (Group) logBook.getParent();
        List<User> possibleUsers = userManager.searchByGroupAndNameNumber(parent.getUid(), userInput);

        if (!possibleUsers.isEmpty()) {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey, user));
            Iterator<User> iterator = possibleUsers.iterator();
            while (menu.getMenuCharLength() < 100 && iterator.hasNext()) {
                User possibleUser = iterator.next();
                menu.addMenuOption(returnUrl(nextMenu, logBookUid) + "&assignUserUid=" + possibleUser.getUid(),
                        possibleUser.nameToDisplay());
            }
        } else {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey + ".no-users", user));
        }

        menu.addMenuOption(returnUrl(backMenu, logBookUid), getMessage(thisSection, pickUserMenu, optionsKey + "back", user));
        menu.addMenuOption(returnUrl(nextMenu, logBookUid), getMessage(thisSection, pickUserMenu, optionsKey + "none", user));

        return menu;
    }

    private String truncateEntryDescription(LogBook entry) {
        StringBuilder stringBuilder = new StringBuilder();
        Pattern pattern = Pattern.compile(" ");
        int maxLength = 30;
        for (String word : pattern.split(entry.getMessage())) {
            if ((stringBuilder.toString().length()+word.length() + 1) > maxLength) {
                break;
            } else
                stringBuilder.append(word).append(" ");
        }
        return stringBuilder.toString();
    }
}