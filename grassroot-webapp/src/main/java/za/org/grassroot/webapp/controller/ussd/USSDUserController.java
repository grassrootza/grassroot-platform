package za.org.grassroot.webapp.controller.ussd;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.NoSuchElementException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * @author luke on 2015/08/14.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDUserController extends USSDController {

    private static final String keyStart = "start", keyName = "name";
    private static final String keyLanguage = "language", keyPhone = "phone";

    /**
     * Starting the user profile management flow here
     */

    @RequestMapping(value = USSD_BASE + USER_MENUS + START_KEY)
    @ResponseBody
    public Request userProfile(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);

        USSDMenu thisMenu = new USSDMenu(getMessage(USER_KEY, START_KEY, PROMPT, sessionUser));

        thisMenu.addMenuOption(USER_MENUS + keyName, getMessage(USER_KEY, START_KEY, OPTION + keyName, sessionUser));
        thisMenu.addMenuOption(USER_MENUS + keyLanguage, getMessage(USER_KEY, START_KEY, OPTION + keyLanguage, sessionUser));
        thisMenu.addMenuOption(USER_MENUS + keyPhone, getMessage(USER_KEY, START_KEY, OPTION + keyPhone, sessionUser));

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyName)
    @ResponseBody
    public Request userDisplayName(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = new USSDMenu("", USER_MENUS + keyName + DO_SUFFIX);

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.hasName()) {
            thisMenu.setPromptMessage(getMessage(USER_KEY, keyName, PROMPT + ".named", sessionUser.getDisplayName(), sessionUser));
        } else {
            thisMenu.setPromptMessage(getMessage(USER_KEY, keyName, PROMPT + ".unnamed", sessionUser));
        }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyName + DO_SUFFIX)
    @ResponseBody
    public Request userChangeName(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                  @RequestParam(value=TEXT_PARAM, required=true) String newName) throws URISyntaxException {

        // todo: add validation and processing of the name that is passed, as well as exception handling etc

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setDisplayName(newName);
        sessionUser = userManager.save(sessionUser);

        return menuBuilder(new USSDMenu(getMessage(USER_KEY, keyName + DO_SUFFIX, PROMPT, sessionUser), optionsHomeExit(sessionUser)));
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyLanguage)
    @ResponseBody
    public Request userPromptLanguage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        USSDMenu thisMenu = new USSDMenu(getMessage(USER_KEY, keyLanguage, PROMPT, sessionUser));
        thisMenu.addMenuOption(USER_MENUS + keyLanguage + DO_SUFFIX + "?language=en", "English");
        thisMenu.addMenuOption(USER_MENUS + keyLanguage + DO_SUFFIX + "?language=zu_ZA", "isiZulu");

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyLanguage + DO_SUFFIX)
    @ResponseBody
    public Request userChangeLanguage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                      @RequestParam(value="language", required=true) String language) throws URISyntaxException {

        User sessionUser;
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setLanguageCode(language);
        sessionUser = userManager.save(sessionUser);

        return menuBuilder(new USSDMenu(getMessage(USER_KEY, keyLanguage + DO_SUFFIX, PROMPT, sessionUser),
                                        optionsHomeExit(sessionUser)));
    }

}