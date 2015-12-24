package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.List;

import static za.org.grassroot.webapp.util.USSDUrlUtil.encodeParameter;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveLogMenu;

/**
 * Created by luke on 2015/12/15.
 */
@RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDLogBookController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDLogBookController.class);
    private static final USSDSection thisSection = USSDSection.LOGBOOK;

    private static final String path = homePath + logMenus;
    private static final String groupMenu = "group", subjectMenu = "subject", dueDateMenu = "due_date",
            assignMenu = "assign", searchUserMenu = "search_user", pickUserMenu = "pick_user", confirmMenu = "confirm", send = "send";

    private static final String logBookParam = "logbookid", logBookUrlSuffix = "?" + logBookParam + "=";

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
    public Request groupList(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        return menuBuilder(ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, subjectMenu));
    }

    @RequestMapping(path + subjectMenu)
    @ResponseBody
    public Request askForSubject(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupIdParam, required = false) Long groupId,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = logBookParam, required = false) Long logBookId) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        String nextUri = (!revising) ? logMenus + dueDateMenu + groupIdUrlSuffix + groupId : returnUrl(confirmMenu, logBookId);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, subjectMenu, promptKey, user), nextUri);
        return menuBuilder(menu);
    }

    @RequestMapping(path + dueDateMenu)
    @ResponseBody
    public Request askForDueDate(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = userInputParam) String userInput,
                                 @RequestParam(value = groupIdParam, required = false) Long groupId,
                                 @RequestParam(value = revisingFlag, required = false) boolean revising,
                                 @RequestParam(value = logBookParam, required = false) Long logBookId) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        if (!revising) logBookId = logBookService.create(user.getId(), groupId, userInput).getId();
        user.setLastUssdMenu(saveLogMenu(dueDateMenu, logBookId));
        return menuBuilder(new USSDMenu(menuPrompt(dueDateMenu, user), nextOrConfirmUrl(assignMenu, logBookId, revising)));
    }

    @RequestMapping(path + assignMenu)
    @ResponseBody
    public Request askForAssignment(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = logBookParam) Long logBookId,
                                    @RequestParam(value = userInputParam) String userInput,
                                    @RequestParam(value = revisingFlag, required=false) boolean revising) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(assignMenu, logBookId));
        if (!revising) logBookService.setDueDate(logBookId, DateTimeUtil.parseDateTime(userInput));
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
                                      @RequestParam(value = revisingFlag, required = false) boolean revising,
                                      @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {

        userInput = (priorInput != null) ? priorInput : userInput;
        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(pickUserMenu, logBookId)
                + "&prior_input=" + encodeParameter(userInput));
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
        menu.addMenuOption(returnUrl(searchUserMenu, logBookId), getMessage(thisSection, pickUserMenu, optionsKey + "none", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + confirmMenu)
    @ResponseBody
    public Request confirmLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = logBookParam) Long logBookId,
                                       @RequestParam(value = userInputParam) String userInput,
                                       @RequestParam(value = previousMenu, required = false) String priorMenu,
                                       @RequestParam(value = "assignUserId", required = false) Long assignUserId) throws URISyntaxException {

        // todo: need a "complete" flag
        // todo: handle interruptions

        boolean assignToUser = (assignUserId != null && assignUserId != 0);
        boolean revising = (priorMenu != null && !priorMenu.trim().equals(""));

        User user = userManager.findByInputNumber(inputNumber, saveLogMenu(confirmMenu, logBookId));

        if (revising) updateLogBookEntry(logBookId, priorMenu, userInput);
        if (assignToUser) logBookService.setAssignedToUser(logBookId, assignUserId);

        // todo: trim the message and other things (for char limit)
        LogBook logBook = logBookService.load(logBookId);
        String formattedDueDate = dateFormat.format(logBook.getActionByDate().toLocalDateTime());
        String assignedUser = (assignToUser) ? userManager.getDisplayName(logBook.getAssignedToUserId()) : "";
        String[] promptFields = new String[] { logBook.getMessage(), groupManager.getGroupName(logBook.getGroupId()),
                formattedDueDate, assignedUser };

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

        // todo: set "complete" and send out notice that entry has been added
        User user = userManager.findByInputNumber(inputNumber, null);
        return menuBuilder(new USSDMenu(menuPrompt(send, user), optionsHomeExit(user)));
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
}
