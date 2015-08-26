package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.LocaleResolver;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by luke on 2015/08/14.
 * todo: Expand -- a lot -- the various methods needed to handle phone number inputs
 */
public class USSDController {

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    EventManagementService eventManager;

    @Autowired
    MessageSource messageSource;

    /**
     * SECTION: Constants used throughout the code
     */

    protected final String baseURI = "http://meeting-organizer.herokuapp.com/ussd/";

    // adopting a convention to capitalize constant strings that are used across all controllers
    // todo: more elegant way of handling "." and "/" difference btw URL mapping and message (but note @RequestMapping can't take a method)

    // Constants used in URL mapping and message handling
    protected static final String USSD_BASE = "/ussd/", MTG_MENUS = "mtg/", USER_MENUS = "user/", GROUP_MENUS = "group/";
    protected static final String VOTE_MENUS = "vote", LOG_MENUS = "log", U404="error"; // leaving off '/' for now, until built
    protected static final String PHONE_PARAM = "msisdn", TEXT_PARAM = "request", GROUP_PARAM = "groupId", EVENT_PARAM = "eventId";
    protected static final String START_KEY = "start", GROUPID_URL = ("?" + GROUP_PARAM + "="),
            EVENTID_URL = ("?" + EVENT_PARAM + "="), PASSED_FIELD = "menukey", DO_SUFFIX = "-do";

    // Constants used in i18n and message handling
    protected static final String HOME_KEY = "home", MTG_KEY = "mtg", USER_KEY = "user", GROUP_KEY = "group", VOTE_KEY = "vote", LOG_KEY = "log";
    protected static final String PROMPT = "prompt", PROMPT_ERROR = "prompt.error", OPTION = "options.", MORE = "more";

    protected final String smsHost = "xml2sms.gsm.co.za";
    protected final String smsUsername = System.getenv("SMSUSER");
    protected final String smsPassword = System.getenv("SMSPASS");


    /**
     * SECTION: Menu building methods
     */

    protected List<Option> createMenu(Map<String, String> menuOptions) throws URISyntaxException {
        List<Option> menuToBuild = new ArrayList<>();
        Integer counter = 1;
        for (Map.Entry<String, String> option : menuOptions.entrySet()) {
            menuToBuild.add(new Option(option.getValue(), counter, counter, new URI(baseURI + option.getKey()), true));
            counter++;
        }
        return menuToBuild;
    }

    // integrating check for menu length in here, to avoid writing it in every return
    // defaulting to not first screen, can do an override in start (shouldn't cause speed issues, but watch)
    // not bothering to check free text input, since the odds of those exceeding are very low (and then a UX issue...)
    protected Request menuBuilder(USSDMenu thisMenu) throws URISyntaxException {
        Request menuRequest;
        if (thisMenu.isFreeText()) {
            menuRequest = new Request(thisMenu.getPromptMessage(), freeText(thisMenu.getNextURI()));
        } else {
            if (checkMenuLength(thisMenu, false))
                menuRequest = new Request(thisMenu.getPromptMessage(), createMenu(thisMenu.getMenuOptions()));
            else
                menuRequest = tooLongError;
        }
        return menuRequest;
    }

    protected List<Option> freeText(String urlEnding) throws URISyntaxException {
        return Collections.singletonList(new Option("", 1, 1, new URI(baseURI + urlEnding), false));
    }

    // method to check the length of a USSD menu and make sure it is under 140/160 chars.
    // at present, am just returning a boolean, but may want to truncate / throw an exception or do something else
    // last: writing it here, so that if we change aggregator / view technology, we adjust here

    protected boolean checkMenuLength(USSDMenu menuToCheck, boolean firstMenu) {

        Integer enumLength = ("1. ").length();
        Integer characterLimit = firstMenu ? 140 : 160;

        return (menuToCheck.getMenuCharLength(enumLength) < characterLimit); // might be able to get away with <=, but prefer to be conservative
    }

    /**
     * SECTION: Auxiliary methods used in creating, sorting, displaying groups in USSD menus / from USSD input
     */

    protected USSDMenu userGroupMenu(User sessionUser, String promptMessage, String path, boolean optionNewGroup)
            throws URISyntaxException {

        List<Group> groupsPartOf = sessionUser.getGroupsPartOf();
        USSDMenu menuBuild = new USSDMenu(promptMessage);
        final String dateFormat = "%1$TD";
        final String formedUrl = (!path.contains("?")) ? (path + GROUPID_URL) : (path + "&" + GROUP_PARAM + "=");

        for (Group groupForMenu : groupsPartOf) {
            String groupName = (groupForMenu.hasName()) ? groupForMenu.getGroupName() :
                    getMessage(GROUP_KEY, "unnamed", "label", String.format(dateFormat, groupForMenu.getCreatedDateTime()), sessionUser);
            menuBuild.addMenuOption(formedUrl + groupForMenu.getId(), groupName);
        }

        if (optionNewGroup)
            menuBuild.addMenuOption(formedUrl + "0", getMessage(GROUP_KEY, "create", "option", sessionUser));

        return menuBuild;
    }

    // todo: remove the version above, for the below, once tested properly

    protected USSDMenu userGroupMenu(User sessionUser, String promptMessage, String existingPath, String newPath)
            throws URISyntaxException {

        // todo: some way to handle pagination if user has many groups -- USSD can only handle five options on a menu ...
        // todo: also, lousy user experience if too many -- should sort by last accessed & most accessed (some combo)
        // todo: switch to using URIComponentsBuilder instead of string joining the parameters

        List<Group> groupsPartOf = sessionUser.getGroupsPartOf();
        USSDMenu menuBuild = new USSDMenu(promptMessage);
        final String dateFormat = "%1$TD";
        final String formedUrl = (!existingPath.contains("?")) ? (existingPath + GROUPID_URL) : (existingPath + "&" + GROUP_PARAM + "=");

        for (Group groupForMenu : groupsPartOf) {
            String groupName = (groupForMenu.hasName()) ? groupForMenu.getGroupName() :
                    getMessage(GROUP_KEY, "unnamed", "label", String.format(dateFormat, groupForMenu.getCreatedDateTime()), sessionUser);
            menuBuild.addMenuOption(formedUrl + groupForMenu.getId(), groupName);
        }

        if (newPath != null)
            menuBuild.addMenuOption(newPath, getMessage(GROUP_KEY, "create", "option", sessionUser));

        return menuBuild;
    }

    /*
     Methods to enter a loop of entering a group, handling input, and exiting again--will be used in several controllers
     note: by luke -- I've moved processing the string into separate phone numbers here, because it's actually a problem
     only for the USSD module. on the web application, and/or the android app, we have a form with validation logic,
     and separate text boxes for each of the numbers, so we get a tidy list of phone number strings
      */

    protected USSDMenu processGroupInput(Long groupId, String userInput, String sectionKey, String menuKey,
                                         String promptKey, String returnUri, User sessionUser) {

        USSDMenu thisMenu = new USSDMenu("");
        thisMenu.setFreeText(true);

        Map<String, List<String>> enteredNumbers = splitPhoneNumbers(userInput, " ");

        List<String> errorNumbers = enteredNumbers.get("error");

        if (errorNumbers.size() == 0) {
            thisMenu.setPromptMessage(getMessage(sectionKey, menuKey + DO_SUFFIX, PROMPT + "." + promptKey, sessionUser));
        } else {
            // assemble the error menu
            String listErrors = String.join(", ", errorNumbers);
            String promptMessage = getMessage(sectionKey, menuKey + DO_SUFFIX, PROMPT_ERROR, listErrors, sessionUser);
            thisMenu.setPromptMessage(promptMessage);
        }

        thisMenu.setNextURI(returnUri  + GROUPID_URL + groupId); // loop back to group menu
        return thisMenu;
    }

    protected Map<String, List<String>> splitPhoneNumbers(String userResponse, String delimiter) {

        // todo: figure out if a more efficient way to return the valid / error split than a map of lists
        // todo: leave the delimiter flexible
        // todo - aakil - also consider asking for a , or something easily entered from keypad # or *
        //                if the number is pasted from contacts it might have spaces in it

        userResponse = userResponse.replace("\"", ""); // in case the response is passed with quotes around it

        Map<String, List<String>> returnMap = new HashMap<>();
        List<String> validNumbers = new ArrayList<>();
        List<String> errorNumbers = new ArrayList<>();

        PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

        for (String inputNumber : Arrays.asList(userResponse.split(delimiter))) {
            try {
                Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputNumber.trim(), "ZA");
                if (phoneNumberUtil.isValidNumber(phoneNumber))
                    validNumbers.add(inputNumber);
                else
                    errorNumbers.add(inputNumber);
            } catch (NumberParseException e) {
                errorNumbers.add(inputNumber);
            }
        }

        returnMap.put("valid", validNumbers);
        returnMap.put("error", errorNumbers);
        return returnMap;
    }

    /**
     * SECTION: i18n methods, as well some default menus used often
     */

    Request tooLongError = new Request("Error! Menu is too long.", new ArrayList<Option>());
    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<Option>());
    Request noGroupError = new Request("Sorry! Something went wrong finding the group.", new ArrayList<Option>());
    Request exitScreen = new Request("Thanks! We're done.", new ArrayList<Option>());

    protected Map<String, String> optionsHomeExit(User sessionUser) {
        return ImmutableMap.<String, String>builder().
                put("start", getMessage(START_KEY, sessionUser)).
                put("exit", getMessage("exit.option", sessionUser)).build();
    }

    protected String getMessage(String section, String menuKey, String messageLocation, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    // convenience function for when passing just a name (of user or group, for example)
    protected String getMessage(String section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, new String[]{ parameter }, new Locale(getLanguage(sessionUser)));
    }

    protected String getMessage(String section, String menuKey, String messageLocation, String[] parameters, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, parameters, new Locale(getLanguage(sessionUser)));
    }

    // for convenience, sometimes easier to read this way than passing around user instance
    protected String getMessage(String section, String menuKey, String messageLocation, Locale sessionLocale) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, sessionLocale);
    }

    // final convenience version, for the root strings, stripping out "."
    protected String getMessage(String messageKey, User sessionUser) {
        return messageSource.getMessage("ussd." + messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    // todo move this somewhere else, and/or clean up nullability in User class, but if put it there, confuses Hibernate (wants a setter)
    protected String getLanguage(User user) {
        // todo some validation on the locale code, above just checking it's not null
        return (user.getLanguageCode() == null) ? "en" : user.getLanguageCode();
    }

}
