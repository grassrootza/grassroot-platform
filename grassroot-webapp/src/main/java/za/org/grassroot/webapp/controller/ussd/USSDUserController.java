package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDUserController extends USSDBaseController {

    private static final String keyStart = "start", keyName = "name";
    private static final String keyLanguage = "language";
    private static final String keyLink = "link";
    private static final USSDSection thisSection = USSDSection.USER_PROFILE;

    @RequestMapping(value = homePath + "rename-start")
    @ResponseBody
    public Request renameAndStart(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = userInputParam) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if ("0".equals(userName) || "".equals(userName.trim())) {
            welcomeMessage = getMessage(USSDSection.HOME, startMenu, promptKey, sessionUser);
            userLogger.recordUserLog(sessionUser.getUid(), UserLogType.USER_SKIPPED_NAME, "");
        } else {
            userManager.updateDisplayName(sessionUser.getUid(), sessionUser.getUid(), userName);
            welcomeMessage = getMessage(USSDSection.HOME, startMenu, promptKey + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
        }
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    @RequestMapping(value = homePath + userMenus + startMenu)
    @ResponseBody
    public Request userProfile(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, sessionUser));

        thisMenu.addMenuOption(userMenus + keyName, getMessage(thisSection, startMenu, optionsKey + keyName, sessionUser));
        thisMenu.addMenuOption(userMenus + keyLanguage, getMessage(thisSection, startMenu, optionsKey + keyLanguage, sessionUser));
        thisMenu.addMenuOption(userMenus+keyLink+doSuffix, getMessage(thisSection,startMenu,optionsKey+keyLink,sessionUser));
        thisMenu.addMenuOption(keyStart, getMessage(thisSection, startMenu, optionsKey + "back", sessionUser));


        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = homePath + userMenus + keyName)
    @ResponseBody
    public Request userDisplayName(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = new USSDMenu("", userMenus + keyName + doSuffix);

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.hasName()) {
            thisMenu.setPromptMessage(getMessage(thisSection, keyName, promptKey + ".named", sessionUser.getDisplayName(), sessionUser));
        } else {
            thisMenu.setPromptMessage(getMessage(thisSection, keyName, promptKey + ".unnamed", sessionUser));
        }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = homePath + userMenus + keyName + doSuffix)
    @ResponseBody
    public Request userChangeName(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                  @RequestParam(value= userInputParam, required=true) String newName) throws URISyntaxException {

        // todo: add validation and processing of the name that is passed, as well as exception handling etc

        User sessionUser = userManager.findByInputNumber(inputNumber);
        userManager.updateDisplayName(sessionUser.getUid(), sessionUser.getUid(), newName);

        return menuBuilder(new USSDMenu(getMessage(thisSection, keyName + doSuffix, promptKey, sessionUser), optionsHomeExit(sessionUser, false)));
    }

    @RequestMapping(value = homePath + userMenus + keyLanguage)
    @ResponseBody
    public Request userPromptLanguage(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, keyLanguage, promptKey, sessionUser));

        for (Map.Entry<String, String> entry : BaseController.getImplementedLanguages().entrySet()) {
            thisMenu.addMenuOption(userMenus + keyLanguage + doSuffix + "?language=" + entry.getKey(), entry.getValue());
        }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = homePath + userMenus + keyLanguage + doSuffix)
    @ResponseBody
    public Request userChangeLanguage(@RequestParam(value= phoneNumber, required=true) String inputNumber,
                                      @RequestParam(value="language", required=true) String language) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        user.setLanguageCode(language); // so next prompt shows up without needing repeat DB query
        userManager.updateUserLanguage(user.getUid(), new Locale(language));

        return menuBuilder(new USSDMenu(getMessage(thisSection, keyLanguage + doSuffix, promptKey, user), optionsHomeExit(user, false)));
    }
    @RequestMapping(value = homePath + userMenus + "link" + doSuffix)
    @ResponseBody
    public Request userSendAndroidLink(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;
        try {
            sessionUser = userManager.findByInputNumber(inputNumber);
            userManager.sendAndroidLinkSms(sessionUser.getUid());
        }
        catch (NoSuchElementException e) { return noUserError; }
        return menuBuilder(new USSDMenu(getMessage(thisSection, keyLink + doSuffix, promptKey, sessionUser),
                optionsHomeExit(sessionUser, false)));
    }

}