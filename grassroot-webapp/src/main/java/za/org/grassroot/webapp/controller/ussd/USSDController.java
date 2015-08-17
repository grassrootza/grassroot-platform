package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.GroupManager;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.UserManager;
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

    String baseURI = "http://meeting-organizer.herokuapp.com/ussd/";
    protected String smsHost = "xml2sms.gsm.co.za";

    protected String smsUsername = System.getenv("SMSUSER");
    protected String smsPassword = System.getenv("SMSPASS");

    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<Option>());
    Request noGroupError = new Request("Sorry! Something went wrong finding the group.", new ArrayList<Option>());

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    public List<Option> createMenu(Map<String, String> menuOptions) throws URISyntaxException {
        List<Option> menuToBuild = new ArrayList<>();
        Integer counter = 1;
        for (Map.Entry<String, String> option : menuOptions.entrySet()) {
            menuToBuild.add(new Option(option.getValue(), counter, counter, new URI(baseURI + option.getKey()), true));
            counter++;
        }
        return menuToBuild;
    }

    public List<Option> freeText(String urlEnding) throws URISyntaxException {
        return Collections.singletonList(new Option("", 1, 1, new URI(baseURI + urlEnding), false));
    }

    public List<Option> userGroupMenu(User userForSession, String path, boolean optionNewGroup) throws URISyntaxException {

        // todo: some way to handle pagination if user has many groups -- USSD can only handle five options on a menu ...
        // todo: also, lousy user experience if too many -- should sort by last accessed & most accessed (some combo)

        List<Group> groupsPartOf = userForSession.getGroupsPartOf();
        List<Option> menuBuilder = new ArrayList<>();
        int listLength = groupsPartOf.size();

        for (int i = 0; i < listLength; i++) {
            Group groupForMenu = groupsPartOf.get(i);
            String groupName = groupForMenu.getGroupName();
            if (groupName == null || groupName.isEmpty())
                groupName = "Unnamed group, created on " + String.format("%1$TD", groupForMenu.getCreatedDateTime());
            menuBuilder.add(new Option(groupName,i+1,i+1, new URI(baseURI+path+"?groupId="+groupForMenu.getId()),true));
        }

        if (optionNewGroup)
            menuBuilder.add(new Option ("Create a new group", listLength+1, listLength+1, new URI(baseURI+path+"?groupId=0"), true));

        return menuBuilder;
    }

}
