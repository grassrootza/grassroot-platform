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
import java.sql.Timestamp;
import java.time.LocalDateTime;

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
            assignMenu = "assign", pickUserMenu = "pick_user", confirmMenu = "confirm";

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
        String nextUri = (revising) ? logMenus + dueDateMenu + groupIdUrlSuffix + groupId : returnUrl(confirmMenu, logBookId);
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
        return menuBuilder(new USSDMenu(menuPrompt(dueDateMenu, user), nextOrConfirmUrl(assignMenu, logBookId, revising)));
    }

    @RequestMapping(path + assignMenu)
    @ResponseBody
    public Request askForAssignment(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = logBookParam) Long logBookId,
                                    @RequestParam(value = userInputParam) String userInput,
                                    @RequestParam(value = revisingFlag) boolean revising) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        if (!revising) logBookService.setDueDate(logBookId, DateTimeUtil.parseDateTime(userInput));
        USSDMenu menu = new USSDMenu(menuPrompt(assignMenu, user));
        menu.addMenuOption(returnUrl(confirmMenu, logBookId), getMessage(thisSection, assignMenu, optionsKey + "group", user));
        menu.addMenuOption(returnUrl(pickUserMenu, logBookId), getMessage(thisSection, assignMenu, optionsKey + "user", user));
        return menuBuilder(menu);
    }

    @RequestMapping(path + pickUserMenu)
    @ResponseBody
    public Request askForUserAssigned(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) Long logBookId,
                                      @RequestParam(value = revisingFlag) boolean revising) throws URISyntaxException {
        // todo: figure out how to make this work, in the parsing
        return null;
    }

    @RequestMapping(path + confirmMenu)
    @ResponseBody
    public Request confirmLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam(value = logBookParam) Long logBookId) {
        // todo: need a "complete" flag
        return null;
    }

    @RequestMapping(path)
    @ResponseBody
    public Request finishLogBookEntry(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = logBookParam) Long logBookId) {
        // todo: set "complete"
        return null;
    }
}
