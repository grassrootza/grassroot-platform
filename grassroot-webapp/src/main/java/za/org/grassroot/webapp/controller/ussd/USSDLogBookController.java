package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static za.org.grassroot.webapp.util.USSDUrlUtil.encodeParameter;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveLogMenu;

/**
 * Created by luke on 2015/12/15.
 */
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDLogBookController extends USSDController {

    public static final String assignUserID = "assignUserId";
    private static final Logger log = LoggerFactory.getLogger(USSDLogBookController.class);
    private static final USSDSection thisSection = USSDSection.LOGBOOK;
    private static final String path = homePath + logMenus;
    private static final String groupMenu = "group", subjectMenu = "subject", dueDateMenu = "due_date",
            assignMenu = "assign", searchUserMenu = "search_user", pickUserMenu = "pick_user", confirmMenu = "confirm", send = "send";
    private static final String entryTypeMenu = "type", listEntriesMenu = "list", viewEntryMenu = "view", viewEntryDates = "view_dates",
            viewAssignment = "view_assigned", setCompleteMenu = "set_complete", viewCompleteMenu = "view_complete",
            completingUser = "choose_completor", pickCompletor = "pick_completor", completedDate = "date_completed",
            confirmCompleteDate = "confirm_date", confirmComplete = "confirm_complete";
    private static final String logBookParam = "logbookid", logBookUrlSuffix = "?" + logBookParam + "=";
    private static final int PAGE_LENGTH = 3;
    private static final int hour =13;
    private static final int minute =00;
    @Autowired
    LogBookService logBookService;

    private String menuPrompt(String menu, User user) {
        return getMessage(thisSection, menu, promptKey, user);
    }

    private String returnUrl(String nextMenu, Long logBookId) {
        return logMenus + nextMenu + logBookUrlSuffix + logBookId;
    }

    private String nextOrConfirmUrl(String nextMenu, Long logBookdId, boolean revising) {
        return revising ? returnUrl(confirmMenu, logBookdId) : returnUrl(nextMenu, logBookdId);
    }

    private String backUrl(String menu, Long logBookId) {
        return returnUrl(menu, logBookId) + "&" + revisingFlag + "=1";
    }


    @RequestMapping(path + startMenu)
    @ResponseBody
    public Request askNewOld(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
        thisMenu.addMenuOption(logMenus + groupMenu + "?new=1", getMessage(thisSection, startMenu, optionsKey + "new", user));
        thisMenu.addMenuOption(logMenus + groupMenu + "?new=0", getMessage(thisSection, startMenu, optionsKey + "old", user));
        return menuBuilder(thisMenu);
    }

    @RequestMapping(path + groupMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = "new") boolean newEntry) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu thisMenu = newEntry ? ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, subjectMenu) :
                ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, entryTypeMenu,
                        getMessage(thisSection, groupMenu, promptKey + ".existing", user));
        return menuBuilder(thisMenu);
    }

    @RequestMapping(path + subjectMenu)
    @ResponseBody
    public Request askForSubject(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupIdParam, required = false) Long groupId,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = logBookParam, required = false) Long logBookId,
                                 @RequestParam(value = interruptedFlag, required=false) boolean interrupted) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        if (!revising) logBookId = logBookService.create(user.getId(), groupId, false).getId();


        user.setLastUssdMenu(saveLogMenu(inputNumber, logBookId));

        String nextUri = (!revising) ? returnUrl(dueDateMenu, logBookId) : returnUrl(confirmMenu, logBookId);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, subjectMenu, promptKey, user), nextUri);
        return menuBuilder(menu);
    }

    @RequestMapping(path + dueDateMenu)
    @ResponseBody
    public Request askForDueDate(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = userInputParam) String userInput,
                                 @RequestParam(value = logBookParam) Long logBookId,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                 @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        userInput = (interrupted) ? priorInput : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(dueDateMenu, logBookId, userInput));
        if (!revising) logBookService.setMessage(logBookId, userInput);
        return menuBuilder(new USSDMenu(menuPrompt(dueDateMenu, user), nextOrConfirmUrl(assignMenu, logBookId, revising)));
    }

    @RequestMapping(path + assignMenu)
    @ResponseBody
    public Request askForAssignment(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = logBookParam) Long logBookId,
                                    @RequestParam(value = userInputParam) String userInput,
                                    @RequestParam(value = revisingFlag, required = false) boolean revising,
                                    @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                    @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        userInput = interrupted ? priorInput : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(assignMenu, logBookId, userInput));
       String formattedDateString =  DateTimeUtil.reformatDateInput(userInput);
        if (!revising) logBookService.setDueDate(logBookId, DateTimeUtil.parsePreformattedDate(
                formattedDateString, hour,minute));
        USSDMenu menu = new USSDMenu(menuPrompt(assignMenu, user));
        menu.addMenuOption(returnUrl(confirmMenu, logBookId), getMessage(thisSection, assignMenu, optionsKey + "group", user));
        menu.addMenuOption(returnUrl(searchUserMenu, logBookId), getMessage(thisSection, assignMenu, optionsKey + "user", user));
        return menuBuilder(menu);
    }


    @RequestMapping(path + searchUserMenu)
    @ResponseBody
    public Request searchForUser(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(searchUserMenu, logBookId));
        USSDMenu menu = new USSDMenu(menuPrompt(searchUserMenu, user), returnUrl(pickUserMenu, logBookId));
        return menuBuilder(menu);
    }

    @RequestMapping(path + pickUserMenu)
    @ResponseBody
    public Request askForUserAssigned(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) Long logBookId,
                                      @RequestParam(value = userInputParam) String userInput,
                                      @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                      @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

        userInput = interrupted ? priorInput : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(pickUserMenu, logBookId, userInput));
        Long groupId = logBookService.load(logBookId).getGroupId();
        List<User> possibleUsers = userManager.searchByGroupAndNameNumber(groupId, userInput);

        USSDMenu menu;
        if (possibleUsers.isEmpty()) {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey + ".no-users", user));
        } else {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey, user));
            for (User possibleUser : possibleUsers) {
                if (menu.getMenuCharLength() < 100) { // should give us space for at least 10 options, but just in case
                    menu.addMenuOption(returnUrl(confirmMenu, logBookId) + "&assignUserId=" + possibleUser.getId(),
                            possibleUser.nameToDisplay());
                } else {
                    break; // todo: there is almost certainly a more elegant way to do this
                }
            }
        }
        menu.addMenuOption(returnUrl(searchUserMenu, logBookId), getMessage(thisSection, pickUserMenu, optionsKey + "back", user));
        menu.addMenuOption(returnUrl(confirmMenu, logBookId), getMessage(thisSection, pickUserMenu, optionsKey + "none", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + confirmMenu)
    @ResponseBody
    public Request confirmLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = logBookParam) Long logBookId,
                                       @RequestParam(value = userInputParam) String userInput,
                                       @RequestParam(value = previousMenu, required = false) String priorMenu,
                                       @RequestParam(value = assignUserID, required = false) Long assignUserId,
                                       @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                       @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {


        // todo: need a "complete" flag

        // todo: handle interruptions
        boolean assignToUser = (assignUserId != null && assignUserId != 0);
        userInput = (priorInput !=null) ? priorInput : userInput;
        priorMenu=(priorMenu!=null)?priorMenu:"";

        boolean revising = (priorMenu != null && !priorMenu.trim().equals("") && !interrupted);
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(confirmMenu, logBookId,
                userInput,priorMenu,assignUserId));

        if (revising) updateLogBookEntry(logBookId, priorMenu, userInput);
        if (assignToUser) logBookService.setAssignedToUser(logBookId, assignUserId);

        // todo: trim the message and other things (for char limit)
        LogBook logBook = logBookService.load(logBookId);
        String formattedDueDate = dateFormat.format(logBook.getActionByDate().toLocalDateTime());
        String assignedUser = (assignToUser) ? userManager.getDisplayName(logBook.getAssignedToUserId()) : "";

        String[] promptFields = new String[]{logBook.getMessage(), groupManager.getGroupName(logBook.getGroupId()),
                formattedDueDate, assignedUser};

        String assignedKey = (assignToUser) ? ".assigned" : ".unassigned";
        USSDMenu menu = new USSDMenu(getMessage(thisSection, confirmMenu, promptKey + assignedKey, promptFields, user));
        menu.addMenuOption(returnUrl(send, logBookId), getMessage(thisSection, confirmMenu, optionsKey + "send", user));
        menu.addMenuOption(backUrl(subjectMenu, logBookId), getMessage(thisSection, confirmMenu, optionsKey + "subject", user));
        menu.addMenuOption(backUrl(dueDateMenu, logBookId), getMessage(thisSection, confirmMenu, optionsKey + "duedate", user));
        menu.addMenuOption(backUrl(assignMenu, logBookId), getMessage(thisSection, confirmMenu, optionsKey + "assign", user));

        return menuBuilder(menu);
    }

    @RequestMapping(path + send)
    @ResponseBody
    public Request finishLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        logBookService.setRecorded(logBookId, true);
        return menuBuilder(new USSDMenu(menuPrompt(send, user), optionsHomeExit(user)));
    }

    /**
     * SECTION: Select and view prior logbook entries
     */
    @RequestMapping(path + entryTypeMenu)
    @ResponseBody
    public Request pickLogBookEntryType(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = groupIdParam) Long groupId) throws URISyntaxException {

        // todo: if either complete or incomplete is empty, skip this menu
        // todo: consider intermediate option of past due date but not yet done
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, entryTypeMenu, promptKey, user));
        String urlBase = logMenus + listEntriesMenu + groupIdUrlSuffix + groupId + "&done=";
        menu.addMenuOption(urlBase + "0", getMessage(thisSection, entryTypeMenu, optionsKey + "notdone", user));
        menu.addMenuOption(urlBase + "1", getMessage(thisSection, entryTypeMenu, optionsKey + "done", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + listEntriesMenu)
    @ResponseBody
    public Request listEntriesMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupIdParam) Long groupId,
                                   @RequestParam(value = "done") boolean doneEntries,
                                   @RequestParam(value = "pageNumber", required = false) Integer pageNumber) throws URISyntaxException {


        pageNumber = (pageNumber == null) ? 0 : pageNumber;
        User user = userManager.findByInputNumber(inputNumber,
                USSDUrlUtil.logViewExistingUrl(listEntriesMenu,groupId,doneEntries,pageNumber));
        String urlBase = logMenus + viewEntryMenu + logBookUrlSuffix;
        Page<LogBook> entries = logBookService.getAllLogBookEntriesForGroup(groupId, pageNumber, PAGE_LENGTH, doneEntries);
        //todo: check sorting on this
        USSDMenu menu;
        if (!entries.hasContent()) {
            if (doneEntries) {
                menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, "complete.noentry", user));
            } else {
                menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, "incomplete.noentry", user));
            }
            menu.addMenuOption(logMenus + entryTypeMenu + groupIdUrlSuffix + groupId, getMessage("options.back", user));
            menu.addMenuOptions(optionsHomeExit(user));

        } else {
            menu = new USSDMenu(getMessage(thisSection, listEntriesMenu, promptKey, user));
            for (LogBook entry : entries) {
                String description = truncateEntryDescription(entry);
                menu.addMenuOption(urlBase + entry.getId(), description);
            }
            if (entries.hasNext()) {
                String nextPageUri = logMenus + listEntriesMenu + groupIdUrlSuffix + groupId + "&done=" + doneEntries + "&pageNumber=" + (pageNumber + 1);
                menu.addMenuOption(nextPageUri, getMessage(thisSection, listEntriesMenu, "more", user));
            }
            if (entries.hasPrevious()) {
                String previousPageUri = logMenus + listEntriesMenu + groupIdUrlSuffix + groupId + "&done=" + doneEntries + "&pageNumber=" + (pageNumber - 1);
                menu.addMenuOption(previousPageUri, getMessage(thisSection, listEntriesMenu, "previous", user));
            }
        }
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewEntryMenu)
    @ResponseBody
    public Request viewEntryMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(viewEntryMenu, logBookId));
        LogBook logBook = logBookService.load(logBookId);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, viewEntryMenu, promptKey, logBook.getMessage(), user));

        // todo: check permissions before deciding what options to display
        menu.addMenuOption(returnUrl(viewEntryDates, logBookId),
                getMessage(thisSection, viewEntryMenu, optionsKey + "dates", user));

        if (logBook.isCompleted()) {
            menu.addMenuOption(returnUrl(viewAssignment, logBookId),
                    getMessage(thisSection, viewEntryMenu, optionsKey + "viewcomplete", user));
        } else {
            if (logBook.getAssignedToUserId() != null && logBook.getAssignedToUserId() != 0)
                menu.addMenuOption(returnUrl(viewAssignment, logBookId), getMessage(thisSection, viewEntryMenu, optionsKey + "assigned", user));
            menu.addMenuOption(returnUrl(setCompleteMenu, logBookId), getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user));
        }

        menu.addMenuOption("exit", "Exit");
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewEntryDates)
    @ResponseBody
    public Request viewLogBookDates(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        LogBook logBook = logBookService.load(logBookId);
        String createdDate = dateFormat.format(logBook.getCreatedDateTime().toLocalDateTime());
        String dueDate = dateFormat.format(logBook.getActionByDate().toLocalDateTime());

        USSDMenu menu;
        if (logBook.isCompleted()) {
            String completedDate = dateFormat.format(logBook.getCompletedDate().toLocalDateTime());
            String userCompleted = (logBook.getCompletedByUserId() == null || logBook.getCompletedByUserId() == 0L) ? "" :
                    "by " + userManager.loadUser(logBook.getCompletedByUserId()).nameToDisplay();
            String[] fields = new String[]{createdDate, dueDate, completedDate, userCompleted};
            menu = new USSDMenu(getMessage(thisSection, viewEntryDates, promptKey + ".complete", fields, user));
        } else {
            String[] fields = new String[]{createdDate, dueDate};
            menu = new USSDMenu(getMessage(thisSection, viewEntryDates, promptKey + ".incomplete", fields, user));
        }

        menu.addMenuOption(logMenus + viewEntryMenu + logBookUrlSuffix + logBookId, getMessage(optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + viewAssignment)
    @ResponseBody
    public Request viewLogBookAssignment(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        LogBook logBook = logBookService.load(logBookId);

        USSDMenu menu;

        String assignedFragment = (logBook.getAssignedToUserId() == null || logBook.getAssignedToUserId() == 0) ?
                getMessage(thisSection, viewAssignment, "group", user) :
                userManager.loadUser(logBook.getAssignedToUserId()).nameToDisplay();
        String completedFragment = logBook.isCompleted() ?
                getMessage(thisSection, viewAssignment, "complete", dateFormat.format(logBook.getCompletedDate().toLocalDateTime()), user) :
                getMessage(thisSection, viewAssignment, "incomplete", dateFormat.format(logBook.getActionByDate().toLocalDateTime()), user);

        menu = new USSDMenu(getMessage(thisSection, viewAssignment, promptKey,
                new String[]{assignedFragment, completedFragment}, user));

        menu.addMenuOption(logMenus + viewEntryMenu + logBookUrlSuffix + logBookId, getMessage(optionsKey + "back", user));
        if (!logBook.isCompleted()) menu.addMenuOption(logMenus + setCompleteMenu + logBookUrlSuffix + logBookId,
                getMessage(thisSection.toKey() + optionsKey + setCompleteMenu, user)); // todo: check permissions
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu)
    @ResponseBody
    public Request setLogBookEntryComplete(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {

        // todo: check permissions
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(setCompleteMenu, logBookId));
        LogBook logBook = logBookService.load(logBookId);

        USSDMenu menu = (logBook.getAssignedToUserId() != null && logBook.getAssignedToUserId() != 0) ?
                new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey + ".assigned",
                        userManager.loadUser(logBook.getAssignedToUserId()).nameToDisplay(), user)) :
                new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey + ".unassigned", user));

        String urlEnd = logBookUrlSuffix + logBookId;
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
                                        @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(completingUser, logBookId));
        USSDMenu menu = new USSDMenu(menuPrompt(searchUserMenu, user), returnUrl(pickCompletor, logBookId));
        return menuBuilder(menu);
    }

    @RequestMapping(path + pickCompletor)
    @ResponseBody
    public Request pickCompletor(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = logBookParam) Long logBookId,
                                 @RequestParam(value = userInputParam) String userInput,
                                 @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                                 @RequestParam(value = interruptedInput, required =false) String prior_input) throws URISyntaxException {
        userInput = interrupted ? prior_input : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(pickCompletor, logBookId, userInput));
        return menuBuilder(pickUserFromGroup(logBookId, userInput, setCompleteMenu + doSuffix, completingUser, user));
    }

    @RequestMapping(path + completedDate)
    @ResponseBody
    public Request enterCompletedDate(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) Long logBookId) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(completedDate, logBookId));
        return menuBuilder(new USSDMenu(getMessage(thisSection, completedDate, promptKey, user),
                returnUrl(confirmCompleteDate, logBookId)));
    }

    @RequestMapping(path + confirmCompleteDate)
    @ResponseBody
    public Request confirmCompletedDate(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam(value = logBookParam) Long logBookId,
                                        @RequestParam(value = userInputParam) String userInput,
                                        @RequestParam(value = interruptedFlag,required=false) boolean interrupted,
                                        @RequestParam(value = interruptedInput, required =false) String priorInput) throws URISyntaxException {

        userInput = (priorInput !=null) ? priorInput : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(confirmCompleteDate, logBookId, userInput));
        String formattedResponse = DateTimeUtil.reformatDateInput(userInput);
        String confirmUrl = returnUrl(setCompleteMenu + doSuffix, logBookId) + "&completed_date=" + encodeParameter(formattedResponse);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, confirmCompleteDate, promptKey, formattedResponse, user));
        menu.addMenuOption(logMenus + confirmUrl, getMessage(thisSection, confirmCompleteDate, optionsKey + "yes", formattedResponse, user));
        menu.addMenuOption(returnUrl(completedDate, logBookId), getMessage(thisSection, confirmCompleteDate, optionsKey + "no", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + setCompleteMenu + doSuffix)
    @ResponseBody
    public Request setLogBookEntryDone(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = logBookParam) Long logBookId,
                                       @RequestParam(value = assignUserID, required = false) Long completedByUserId,
                                       @RequestParam(value = "completed_date", required = false) String completedDate) throws URISyntaxException {
        // todo: check permissions
        User user = userManager.findByInputNumber(inputNumber, null);
        if (completedByUserId == null) completedByUserId = 0L;
        logBookService.setCompleted(logBookId, completedByUserId, DateTimeUtil.reformatDateInput(completedDate));

        USSDMenu menu = new USSDMenu(getMessage(thisSection, setCompleteMenu, promptKey, user));
        // todo: consider adding option to go back to either section start or group logbook start
        menu.addMenuOptions(optionsHomeExit(user));
        return menuBuilder(menu);
    }

    private void updateLogBookEntry(Long logBookId, String field, String value) {
        switch (field) {
            case subjectMenu:
                logBookService.setMessage(logBookId, value);
                break;
            case dueDateMenu:
                logBookService.setDueDate(logBookId, DateTimeUtil.parseDateTime(value)); // todo: split these, as in meetings
                break;
        }
    }

    private USSDMenu pickUserFromGroup(Long logBookId, String userInput, String nextMenu, String backMenu, User user) {

        USSDMenu menu;
        List<User> possibleUsers = userManager.searchByGroupAndNameNumber(logBookService.load(logBookId).getGroupId(), userInput);

        if (!possibleUsers.isEmpty()) {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey, user));
            Iterator<User> iterator = possibleUsers.iterator();
            while (menu.getMenuCharLength() < 100 && iterator.hasNext()) {
                User possibleUser = iterator.next();
                menu.addMenuOption(returnUrl(nextMenu, logBookId) + "&assignUserId=" + possibleUser.getId(),
                        possibleUser.nameToDisplay());
            }
        } else {
            menu = new USSDMenu(getMessage(thisSection, pickUserMenu, promptKey + ".no-users", user));
        }

        menu.addMenuOption(returnUrl(backMenu, logBookId), getMessage(thisSection, pickUserMenu, optionsKey + "back", user));
        menu.addMenuOption(returnUrl(nextMenu, logBookId), getMessage(thisSection, pickUserMenu, optionsKey + "none", user));

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