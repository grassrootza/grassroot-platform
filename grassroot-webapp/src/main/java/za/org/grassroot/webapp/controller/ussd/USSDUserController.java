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

    private static final String USER_MENUS = "user/";
    private static final String keyStart = "start", keyPromptName = "name", keyRenameDo = "name2";
    private static final String keyPromptLanguage = "language", keyPromptPhone = "phone";

    private static final Map<String, USSDMenu> menuFlow = ImmutableMap.<String,USSDMenu>builder().
            put(keyStart, new USSDMenu(USER_MENUS + keyStart, "What would you like to do?", false)).
            put(keyPromptName, new USSDMenu(USER_MENUS + keyPromptName, "You haven't set your name yet. What should we call you?", USER_MENUS + keyRenameDo)).
            put(keyRenameDo, new USSDMenu(USER_MENUS + keyRenameDo, "Done! Name changed.", optionsHomeExit)).
            put(keyPromptLanguage, new USSDMenu(USER_MENUS + keyPromptLanguage, "Sorry, not built yet." , optionsHomeExit)).
            put(keyPromptPhone, new USSDMenu(USER_MENUS + keyPromptPhone, "Sorry, not built yet.", optionsHomeExit)).build();

    /**
     * Starting the user profile management flow here
     */

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyStart)
    @ResponseBody
    public Request userProfile(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = menuFlow.get(keyStart);

        thisMenu.addMenuOption(menuFlow.get(keyPromptName).getUri(), "Change my display name");
        thisMenu.addMenuOption(menuFlow.get(keyPromptLanguage).getUri(), "Change my language");
        thisMenu.addMenuOption(menuFlow.get(keyPromptPhone).getUri(), "Add phone number to my profile");

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyPromptName)
    @ResponseBody
    public Request userDisplayName(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = menuFlow.get(keyPromptName);

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        if (sessionUser.hasName()) {
            thisMenu.setPromptMessage("Your name is currently set to '" + sessionUser.getDisplayName() +
                    "'. What do you want to change it to?");
        }

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = USSD_BASE + USER_MENUS + keyRenameDo)
    @ResponseBody
    public Request userChangeName(@RequestParam(value=PHONE_PARAM, required=true) String inputNumber,
                                  @RequestParam(value=TEXT_PARAM, required=true) String newName) throws URISyntaxException {

        USSDMenu thisMenu = menuFlow.get(keyRenameDo);

        // todo: add validation and processing of the name that is passed, as well as exception handling etc

        User sessionUser = new User();
        try { sessionUser = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchElementException e) { return noUserError; }

        sessionUser.setDisplayName(newName);
        sessionUser = userManager.save(sessionUser);

        return menuBuilder(thisMenu);
    }

}