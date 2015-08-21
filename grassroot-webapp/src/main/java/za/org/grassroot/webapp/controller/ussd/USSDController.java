package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.GroupManager;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.UserManager;
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

    protected final String baseURI = "http://meeting-organizer.herokuapp.com/ussd/";

    // adopting a convention to capitalize constant strings that are used across all controllers
    protected static final String USSD_BASE = "/ussd/", MTG_MENUS = "mtg/", USER_MENUS = "user/", GROUP_MENUS = "group/";
    protected static final String VOTE_MENUS = "vote", LOG_MENUS = "log", U404="error"; // leaving off '/' for now, until built
    protected static final String PHONE_PARAM = "msisdn", TEXT_PARAM = "request", GROUP_PARAM = "groupId", EVENT_PARAM = "eventId";
    protected static final String START_KEY = "start", GROUPID_URL = ("?" + GROUP_PARAM + "="), DO_SUFFIX = "-do";


    protected final String smsHost = "xml2sms.gsm.co.za";
    protected final String smsUsername = System.getenv("SMSUSER");
    protected final String smsPassword = System.getenv("SMSPASS");

    Request tooLongError = new Request("Error! Menu is too long.", new ArrayList<Option>());
    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<Option>());
    Request noGroupError = new Request("Sorry! Something went wrong finding the group.", new ArrayList<Option>());
    Request exitScreen = new Request("Thanks! We're done.", new ArrayList<Option>());

    protected static final Map<String, String> optionsHomeExit = ImmutableMap.<String, String>builder().
            put("start", "Go back to the main menu").
            put("exit", "Exit GrassRoot").build();

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

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

    public USSDMenu userGroupMenu(User userForSession, String promptMessage, String path, boolean optionNewGroup)
            throws URISyntaxException {

        // todo: some way to handle pagination if user has many groups -- USSD can only handle five options on a menu ...
        // todo: also, lousy user experience if too many -- should sort by last accessed & most accessed (some combo)

        List<Group> groupsPartOf = userForSession.getGroupsPartOf();
        USSDMenu menuBuild = new USSDMenu(promptMessage);
        String unnamedPrefix = "Group created on ", dateFormat = "%1$TD";
        String formedUrl = path + GROUPID_URL;

        for (Group groupForMenu : groupsPartOf) {
            String groupName = (groupForMenu.hasName()) ? groupForMenu.getGroupName() :
                    (unnamedPrefix + String.format("%1$TD", groupForMenu.getCreatedDateTime()));
            menuBuild.addMenuOption(formedUrl + groupForMenu.getId(), groupName);
        }

        if (optionNewGroup)
            menuBuild.addMenuOption(formedUrl + "0", "Create a new group");

        return menuBuild;
    }

}
