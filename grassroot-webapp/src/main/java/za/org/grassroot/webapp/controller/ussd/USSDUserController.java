package za.org.grassroot.webapp.controller.ussd;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.integration.location.TownLookupResult;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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

    private final AddressBroker addressBroker;
    private LocationInfoBroker locationInfoBroker;

    @Autowired
    public USSDUserController(AddressBroker addressBroker) {
        this.addressBroker = addressBroker;
    }

    @Autowired(required = false)
    public void setLocationInfoBroker(LocationInfoBroker locationInfoBroker) {
        this.locationInfoBroker = locationInfoBroker;
    }

    @RequestMapping(value = homePath + "rename-start")
    @ResponseBody
    public Request renameAndStart(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = userInputParam) String userName) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);
        String welcomeMessage;
        if ("0".equals(userName) || "".equals(userName.trim())) {
            welcomeMessage = getMessage(USSDSection.HOME, startMenu, promptKey, sessionUser);
            userLogger.recordUserLog(sessionUser.getUid(), UserLogType.USER_SKIPPED_NAME, "", UserInterfaceType.USSD);
        } else {
            userManager.updateDisplayName(sessionUser.getUid(), sessionUser.getUid(), userName.trim());
            welcomeMessage = getMessage(USSDSection.HOME, startMenu, promptKey + "-rename-do", sessionUser.nameToDisplay(), sessionUser);
        }
        return menuBuilder(welcomeMenu(welcomeMessage, sessionUser));
    }

    @RequestMapping(value = homePath + userMenus + startMenu)
    @ResponseBody
    public Request userProfile(@RequestParam(value= phoneNumber) String inputNumber) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber);

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, sessionUser));

        thisMenu.addMenuOption(userMenus + keyName, getMessage(thisSection, startMenu, optionsKey + keyName, sessionUser));
        thisMenu.addMenuOption(userMenus + keyLanguage, getMessage(thisSection, startMenu, optionsKey + keyLanguage, sessionUser));
        thisMenu.addMenuOption(userMenus + "town", getMessage(thisSection, startMenu, optionsKey + "town", sessionUser));
        thisMenu.addMenuOption(userMenus + "email", getMessage(thisSection, startMenu, optionsKey + "email", sessionUser));
        thisMenu.addMenuOption(userMenus+keyLink+doSuffix, getMessage(thisSection,startMenu,optionsKey+keyLink,sessionUser));
        thisMenu.addMenuOption(keyStart, getMessage(thisSection, startMenu, optionsKey + "back", sessionUser));

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = homePath + userMenus + keyName)
    @ResponseBody
    public Request userDisplayName(@RequestParam(value= phoneNumber, required=true) String inputNumber) throws URISyntaxException {

        USSDMenu thisMenu = new USSDMenu("", userMenus + keyName + doSuffix);
        User sessionUser = userManager.findByInputNumber(inputNumber);

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
        User sessionUser = userManager.findByInputNumber(inputNumber);
        userManager.updateDisplayName(sessionUser.getUid(), sessionUser.getUid(), newName.trim());
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
    public Request userChangeLanguage(@RequestParam(value= phoneNumber) String inputNumber,
                                      @RequestParam String language) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        user.setLanguageCode(language); // so next prompt shows up without needing repeat DB query
        userManager.updateUserLanguage(user.getUid(), new Locale(language), UserInterfaceType.USSD);

        return menuBuilder(new USSDMenu(getMessage(thisSection, keyLanguage + doSuffix, promptKey, user), optionsHomeExit(user, false)));
    }

    @RequestMapping(value = homePath + userMenus + "link" + doSuffix)
    @ResponseBody
    public Request userSendAndroidLink(@RequestParam(value= phoneNumber) String inputNumber) throws URISyntaxException {
        User sessionUser;
        try {
            sessionUser = userManager.findByInputNumber(inputNumber);
            userManager.sendAndroidLinkSms(sessionUser.getUid());
        }
        catch (NoSuchElementException e) { return noUserError; }
        return menuBuilder(new USSDMenu(getMessage(thisSection, keyLink + doSuffix, promptKey, sessionUser),
                optionsHomeExit(sessionUser, false)));
    }

    @RequestMapping(value = homePath + userMenus + "email")
    @ResponseBody public Request alterEmailPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        final String prompt = !user.hasEmailAddress() ? getMessage("user.email.prompt.none", user) :
                getMessage(thisSection, "email", "prompt.set", user.getEmailAddress(), user);
        return menuBuilder(new USSDMenu(prompt, userMenus + "email/set"));
    }

    @RequestMapping(value = homePath + userMenus + "email/set")
    @ResponseBody public Request setEmail(@RequestParam(value = phoneNumber) String inputNumber,
                                          @RequestParam(value = userInputParam) String email) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;
        if (!EmailValidator.getInstance().isValid(email)) {
            menu = new USSDMenu(getMessage("user.email.prompt.invalid", user), userMenus + "email/set");
        } else if (userManager.emailTaken(user.getUid(), email)) {
            menu = new USSDMenu(getMessage("user.email.prompt.taken", user), userMenus + "email/set");
        } else {
            userManager.updateEmailAddress(user.getUid(), user.getUid(), email);
            menu = new USSDMenu(getMessage("user.email.prompt.done", user), optionsHomeExit(user, false));
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = homePath + userMenus + "town")
    @ResponseBody public Request townPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
        final String prompt = getMessage(thisSection, "town", "prompt", user);
        return menuBuilder(new USSDMenu(prompt, userMenus + "town/select"));
    }

    @RequestMapping(value = homePath + userMenus + "town/select")
    @ResponseBody public Request townOptions(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam(value = userInputParam) String userInput) throws URISyntaxException {
        UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
        if ("0".equals(userInput.trim()))
            return menuBuilder(new USSDMenu(getMessage("campaign.exit_positive.generic", user)));

        final List<TownLookupResult> placeDescriptions = locationInfoBroker.lookupPostCodeOrTown(userInput, user.getProvince());
        if (placeDescriptions == null || placeDescriptions.isEmpty()) {
            final String prompt = getMessage(thisSection, "town", "none.prompt", user);
            return menuBuilder(new USSDMenu(prompt, userMenus + "town/select"));
        } else if (placeDescriptions.size() == 1) {
            final String prompt = getMessage(thisSection, "town", "one.prompt", placeDescriptions.get(0).getDescription(), user);
            USSDMenu menu = new USSDMenu(prompt);
            menu.addMenuOption(userMenus + "town/confirm?placeId=" + placeDescriptions.get(0).getPlaceId(), getMessage("options.yes", user));
            menu.addMenuOption(userMenus + "town", getMessage("options.no", user));
            return menuBuilder(menu);
        } else {
            final String prompt = getMessage(thisSection, "town", "many.prompt", user);
            final USSDMenu menu = new USSDMenu(prompt);
            menu.addMenuOptions(placeDescriptions.stream().collect(Collectors.toMap(
                    lookup -> userMenus + "town/confirm?placeId=" + USSDUrlUtil.encodeParameter(lookup.getPlaceId()),
                    TownLookupResult::getDescription)));
            return menuBuilder(menu);
        }
    }

    @RequestMapping(value = homePath + userMenus + "town/confirm")
    @ResponseBody public Request townConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam String placeId) throws URISyntaxException {
        UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
        addressBroker.setUserAreaFromUSSD(user.getUid(), placeId, LocationSource.TOWN_LOOKUP, true);
        USSDMenu menu = new USSDMenu(getMessage("user.town.updated.prompt", user));
        menu.addMenuOptions(optionsHomeExit(user, true));
        return menuBuilder(menu);
    }

}