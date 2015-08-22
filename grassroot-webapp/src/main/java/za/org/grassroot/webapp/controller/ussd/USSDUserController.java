package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.Map;
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

    private static final Map<String, USSDMenu> menuFlow = ImmutableMap.<String,USSDMenu>builder().
            put(keyStart, new USSDMenu("What would you like to do?")).
            put(keyName, new USSDMenu("You haven't set your name yet. What should we call you?", USER_MENUS + keyName + DO_SUFFIX)).
            put(keyName + DO_SUFFIX, new USSDMenu("Done! Name changed.", optionsHomeExit)).
            put(keyLanguage, new USSDMenu("Sorry, not built yet." , optionsHomeExit)).
            put(keyPhone, new USSDMenu("Sorry, not built yet.", optionsHomeExit)).build();

    /**
     * Starting the user profile management flow here
     */

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyStart)
    @ResponseBody
    public Request userProfile(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = menuFlow.get(keyStart);

        thisMenu.addMenuOption(USER_MENUS + keyName, "Change my display name");
        thisMenu.addMenuOption(USER_MENUS + keyLanguage, "Change my language");
        thisMenu.addMenuOption(USER_MENUS + keyPhone, "Add phone number to my profile");

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyName)
    @ResponseBody
    public Request userDisplayName(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = menuFlow.get(keyName);

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.hasName()) {
            thisMenu.setPromptMessage("Your name is currently set to '" + sessionUser.getDisplayName() +
                    "'. What do you want to change it to?");
        }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyName + DO_SUFFIX)
    @ResponseBody
    public Request userChangeName(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                  @RequestParam(value=TEXT_PARAM, required=true) String newName) throws URISyntaxException {

        USSDMenu thisMenu = menuFlow.get(keyName + DO_SUFFIX);

        // todo: add validation and processing of the name that is passed, as well as exception handling etc

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setDisplayName(newName);
        sessionUser = userManager.save(sessionUser);

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyLanguage)
    @ResponseBody
    public Request userPromptLanguage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = new USSDMenu("What language do you want?");
        thisMenu.addMenuOption(USER_MENUS + keyLanguage + DO_SUFFIX + "?language=en", "English");
        thisMenu.addMenuOption(USER_MENUS + keyLanguage + DO_SUFFIX + "?language=zu", "isiZulu");

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyLanguage + DO_SUFFIX)
    @ResponseBody
    public Request userChangeLanguage(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                      @RequestParam(value="language", required=true) String language) throws URISyntaxException {

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setLanguageCode(language);
        sessionUser = userManager.save(sessionUser);

        return menuBuilder(new USSDMenu(getMessage(USER_KEY, keyLanguage + DO_SUFFIX, PROMPT, sessionUser),
                                        optionsHomeExit(sessionUser)));
    }

}