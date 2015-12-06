package za.org.grassroot.webapp.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.webapp.controller.ussd.USSDController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/12/04.
 * Consolidating methods to deal with groups in USSD methods. These fall into two categories:
 * (1) Fetching a list of the groups that the user is part of / created / can perform an action on, and putting them in a menu
 * (2) Creating a group, through "create group", or via other means
 */
@Component
public class USSDGroupUtil extends USSDUtil {

    @Autowired
    GroupManagementService groupManager;

    private static final String groupKeyForMessages = "group";
    private static final String groupIdParameter = "groupId";
    private static final String groupIdUrlEnding = "?" + groupIdParameter + "=";
    private static final String validNumbers = "valid";
    private static final String invalidNumbers = "error";

    private static final SimpleDateFormat unnamedGroupDate = new SimpleDateFormat("d MMM");

    /**
     * SECTION 1: menus to ask a user to pick a group, including via pagination
     * major todo: introduce filtering by permission into these
     */

    public USSDMenu askForGroupAllowCreateNew(User sessionUser, USSDSection section, String nextUrl,
                                              String newGroupUrl, String nonGroupParams) throws URISyntaxException {

        USSDMenu groupMenu;
        if (sessionUser.getGroupsPartOf().isEmpty()) {
            groupMenu = createGroupPrompt(sessionUser, section, newGroupUrl);
        } else {
            String prompt = getMessage(section.toKey(), groupKeyForMessages, promptKey + ".existing", sessionUser);
            String existingGroupUri = section.toPath() + nextUrl + nonGroupParams;
            String newGroupUri = section.toPath() + newGroupUrl + nonGroupParams;
            groupMenu = userGroupMenuPageOne(sessionUser, prompt, existingGroupUri, newGroupUri);
        }
        return groupMenu;
    }

    public USSDMenu askForGroupNoInlineNew(User sessionUser, USSDSection section, String promptIfExisting, String promptIfEmpty,
                                           String urlIfExisting, String urlIfEmpty, String nonGroupParams) throws URISyntaxException {
        USSDMenu groupMenu;
        // todo: replace the getter with a less expensive call
        if(sessionUser.getGroupsPartOf().isEmpty()) {
            groupMenu = new USSDMenu(promptIfEmpty);
            groupMenu.addMenuOption(urlIfEmpty, getMessage(section.toKey(), groupKeyForMessages, "options.new", sessionUser));
            groupMenu.addMenuOption("exit", getMessage("exit", sessionUser));
        } else {
            String existingGroupUri = section.toPath() + urlIfExisting + nonGroupParams;
            groupMenu = userGroupMenuPageOne(sessionUser, promptIfExisting, existingGroupUri, null);
        }
        return groupMenu;
    }

    public USSDMenu userGroupMenuPageOne(User user, String prompt, String existingGroupUrl, String newGroupUrl) throws URISyntaxException {
        return userGroupMenuPaginated(user, prompt, existingGroupUrl, newGroupUrl, 0);
    }

    public USSDMenu userGroupMenuPaginated(User user, String prompt, String urlForExistingGroups, String urlForNewGroup, Integer pageNumber)
            throws URISyntaxException {
        USSDMenu menu = new USSDMenu(prompt);
        Page<Group> groupsPartOf = groupManager.getPageOfActiveGroups(user, pageNumber, PAGE_LENGTH);
        menu = addListOfGroupsToMenu(menu, urlForExistingGroups, groupsPartOf.getContent(), user);
        if (groupsPartOf.hasNext())
            menu.addMenuOption(USSDUrlUtil.assemblePaginatedURI(prompt, urlForExistingGroups, urlForNewGroup, pageNumber + 1),
                               "More groups"); // todo: i18n
        if (groupsPartOf.hasPrevious())
            menu.addMenuOption(USSDUrlUtil.assemblePaginatedURI(prompt, urlForExistingGroups, urlForNewGroup, pageNumber - 1),
                               "Back"); // todo: i18n
        if (urlForNewGroup != null)
            menu.addMenuOption(urlForNewGroup, getMessage(groupKeyForMessages, "create", "option", user));
        return menu;
    }

    public USSDMenu addListOfGroupsToMenu(USSDMenu menu, String nextMenuUrl, List<Group> groups, User user) {
        final String formedUrl = (!nextMenuUrl.contains("?")) ?
                (nextMenuUrl + groupIdUrlEnding) :
                (nextMenuUrl + "&" + groupIdParameter + "=");
        for (Group group : groups) {
            String name = (group.hasName()) ? group.getGroupName() :
                    getMessage(groupKeyForMessages, "unnamed", "label", unnamedGroupDate.format(group.getCreatedDateTime()), user);
            menu.addMenuOption(formedUrl + group.getId(), name);
        }
        return menu;
    }

    /**
     * SECTION 2: Methods to enter numbers for creating a new group, handling input, and exiting again
     * */


    public USSDMenu createGroupPrompt(User user, USSDSection section, String nextUrl) throws URISyntaxException {
        USSDMenu thisMenu = new USSDMenu(getMessage(section.toKey(), groupKeyForMessages, promptKey + ".create", user));
        thisMenu.setNextURI(nextUrl);
        return thisMenu;
    }

    public USSDMenu addNumbersToNewGroup(User user, USSDSection section, String userInput, String returnUrl) throws URISyntaxException {
        USSDMenu menu = new USSDMenu(true);
        final Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        if (enteredNumbers.get(validNumbers).isEmpty()) {
            // do not create a group, just tell the user that no numbers are valid, please try again
            menu.setPromptMessage("Nope, didn't work");
            menu.setNextURI(section.toPath() + returnUrl);
        } else {
            Group createdGroup = groupManager.createNewGroup(user, enteredNumbers.get(validNumbers));
            menu = checkForErrorsAndSetPrompt(menu, createdGroup.getId(), section, enteredNumbers.get(invalidNumbers), returnUrl);
        }
        return menu;
    }

    public USSDMenu addNumbersToExistingGroup(User user, Long groupId, USSDSection section, String userInput, String returnUrl)
            throws URISyntaxException {
        USSDMenu menu = new USSDMenu(true);
        Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        groupManager.addNumbersToGroup(groupId, enteredNumbers.get(validNumbers));
        return checkForErrorsAndSetPrompt(menu, groupId, section, enteredNumbers.get(invalidNumbers), returnUrl);
    }

    private USSDMenu checkForErrorsAndSetPrompt(USSDMenu menu, Long groupId, USSDSection section, List<String> invalidNumbers,
                                                String returnUrl) {
        String prompt = invalidNumbers.isEmpty() ? "" : "";
        menu.setPromptMessage(prompt);
        String groupIdWithParams = (returnUrl.contains("?")) ? ("&" + groupIdParameter + "=") : groupIdUrlEnding;
        menu.setNextURI(section.toPath() + returnUrl + groupIdWithParams + groupId);
        return menu;
    }

}
