package za.org.grassroot.webapp.util;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.GroupJoinRequestService;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static za.org.grassroot.webapp.enums.USSDSection.GROUP_MANAGER;
import static za.org.grassroot.webapp.enums.USSDSection.SAFETY_GROUP_MANAGER;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/12/04.
 * Consolidating methods to deal with groups in USSD methods. These fall into two categories:
 * (1) Fetching a list of the groups that the user is part of / created / can perform an action on, and putting them in a menu
 * (2) Creating a group, through "create group", or via other means
 */
@Component
public class USSDGroupUtil extends USSDUtil {

    private static final Logger log = LoggerFactory.getLogger(USSDGroupUtil.class);

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private GroupJoinRequestService groupJoinRequestService;

    @Value("${grassroot.ussd.joincode.format:*134*1994*%s#}")
    private String joinCodeFormat;

    private static final String groupKeyForMessages = "group";

    private static final String groupUidParameter = "groupUid";
    private static final String groupUidUrlEnding = "?" + groupUidParameter + "=";

    private static final String validNumbers = "valid";
    private static final String invalidNumbers = "error";
    private static final String
            existingGroupMenu = "menu",
            unsubscribePrompt = "unsubscribe",
            groupTokenMenu = "token",
            renameGroupPrompt = "rename",
            hideGroup = "hidegroup",
            showGroup = "showgroup",
            visibility = "visibility",
            addMemberPrompt = "addnumber",
            inactiveMenu = "inactive",
            advancedGroupMenu = "advanced",
            listGroupMembers = "list"; // actually just gives a count

    public static final DateTimeFormatter unnamedGroupDate = DateTimeFormatter.ofPattern("d MMM");

    private static final Map<USSDSection, Permission> SectionPermissionMap = ImmutableMap.of(
            USSDSection.MEETINGS, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            USSDSection.VOTES, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            USSDSection.TODO, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);

    /**
     * SECTION 1: menus to ask a user to pick a group, including via pagination
     */

    /**
     * Core method, to present user a list of groups to take further action:
     * (a) user has no groups, so just ask for one and then go to the next page (goes to newGroupMenu)
     * (b) user has groups, and there are open join requests on them which the user can answer
     * (c) user has groups, ask for one (goes to section + nextUrl), with option to create new (goes to section + urlForCreatingNewGroup);
     * @param user currently active user
     * @param section section of the ussd menu that is currently in view
     * @param urlForExistingGroup option link if user belongs to one or more groups
     * @param urlForNewGroupPrompt option link to a menu prompting for new group, pass null if option should not be displayed
     * @param urlToCreateNewGroup option link to actually create group (if should skip straight to it)
     * @param urlIfNogroups option link if direct group creation is not possible but a way out should be offered users
     * @return
     * @throws URISyntaxException
     */

    public USSDMenu askForGroup(User user, USSDSection section, String urlForExistingGroup, String urlForNewGroupPrompt,
                                String urlToCreateNewGroup, String urlIfNogroups, Integer numberGroups) throws URISyntaxException {

        USSDMenu groupMenu;

        final Permission filter = SectionPermissionMap.get(section); // returning null is what we want if key not present (e.g., for groups section)
        final int groupCount = numberGroups == null ? permissionBroker.countActiveGroupsWithPermission(user, filter) : numberGroups;

        if (groupCount == 0) { // case (a), ask for a name and then go to the next menu
            if (!StringUtils.isEmpty(urlToCreateNewGroup)) {
                groupMenu = createGroupPrompt(user, section, urlToCreateNewGroup);
            } else { // handle no new groups, with redirect option
                groupMenu = new USSDMenu(getMessage(section, "group", promptKey + ".empty", user)); // todo : make sure all sections use this syntax
                groupMenu.addMenuOption(urlIfNogroups, getMessage(section, groupKeyForMessages, "options.new", user));
                groupMenu.addMenuOption("start", getMessage("start", user));
                groupMenu.addMenuOption("exit", getMessage("exit.option", user));
            }
        } else { // case (c), ask for an existing one, with option to prompt for new one
            String prompt = getMessage(section, groupKeyForMessages, promptKey + ".existing", user); // todo : make sure all sections user this syntax
            String existingGroupUri = section.toPath() + urlForExistingGroup;
            String newGroupUri = StringUtils.isEmpty(urlForNewGroupPrompt) ? GROUP_MANAGER.toPath() + "create" : section.toPath() + urlForNewGroupPrompt;
            groupMenu = userGroupMenuPaginated(user, prompt, existingGroupUri, newGroupUri, 0, groupCount, section);
        }
        return groupMenu;
    }

    public USSDMenu showGroupRequests(User user, USSDSection section) {
        List<GroupJoinRequest> requests = groupJoinRequestService.getPendingRequestsForUser(user.getUid());
        GroupJoinRequest request;
        if (requests != null && !requests.isEmpty()) {
            request = requests.get(0);
        } else {
            throw new UnsupportedOperationException("Error! Group join menu should not be created if no pending requests");
        }
        String displayName = request.getRequestor().getDisplayName();
        String groupName = request.getGroup().getGroupName();
        final String prompt = getMessage(section.toString(), groupKeyForMessages, promptKey + ".join_request", new String[]{displayName, groupName}, user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(approveRejectRequestMenuUrl("approve", user.getUid(), request.getUid()), getMessage(section, groupKeyForMessages, optionsKey + "approve", user));
        menu.addMenuOption(approveRejectRequestMenuUrl("reject", user.getUid(), request.getUid()), getMessage(section, groupKeyForMessages, optionsKey + "reject", user));
        return menu;
    }

    public USSDMenu showUserCreatedGroupsForSafetyFeature(User user, USSDSection section, String urlForExistingGroups, Integer pageNumber) throws URISyntaxException {
        final Page<Group> groupsCreated = groupBroker.fetchUserCreatedGroups(user, pageNumber, PAGE_LENGTH);
        final String prompt = getMessage(section, groupKeyForMessages, promptKey + ".existing", user);

        USSDMenu menu = new USSDMenu(prompt);
        menu = addGroupsToMenu(menu, urlForExistingGroups, groupsCreated.getContent(), user);

        if (groupsCreated.hasNext())
            menu.addMenuOption(paginatedGroupUrl(prompt, urlForExistingGroups, null, SAFETY_GROUP_MANAGER, pageNumber + 1), getMessage("group.more", user));
        if (groupsCreated.hasPrevious()) {
            menu.addMenuOption(paginatedGroupUrl(prompt, urlForExistingGroups, null, SAFETY_GROUP_MANAGER, pageNumber - 1), getMessage("group.back", user));
        } else {
            menu.addMenuOption(section.toPath() + "start", getMessage(section, groupKeyForMessages, optionsKey + "home", user));
        }
        return menu;
    }

    /**
     * @param user                 user currently in session
     * @param prompt               ussd prompt
     * @param urlForExistingGroups the url for exisitng groups
     * @param urlForNewGroup       url for new groups
     * @param pageNumber           current page
     * @param totalResults         the number of results corresponding to this query/filter
     * @param section              current section  @return
     * @throws URISyntaxException
     */
    public USSDMenu userGroupMenuPaginated(User user, String prompt, String urlForExistingGroups, String urlForNewGroup,
                                           int pageNumber, Integer totalResults, USSDSection section) throws URISyntaxException {

        USSDMenu menu = new USSDMenu(prompt);
        Permission filter = SectionPermissionMap.get(section); // returning null is what we want if key not present
        long startTime = System.currentTimeMillis();

        List<Group> groupsPartOf = permissionBroker.getPageOfGroups(user, filter, pageNumber, PAGE_LENGTH);
        log.info("Getting groups for USSD menu, took {} msecs, permission={}", System.currentTimeMillis() - startTime, filter);

        menu = addGroupsToMenu(menu, urlForExistingGroups, groupsPartOf, user);

        int maxResults = totalResults == null ? permissionBroker.countActiveGroupsWithPermission(user, filter) : totalResults;

        if (pageNumber * PAGE_LENGTH < maxResults)
            menu.addMenuOption(paginatedGroupUrl(prompt, urlForExistingGroups, urlForNewGroup, section, pageNumber + 1), getMessage("group.more", user));
        if (pageNumber > 0)
            menu.addMenuOption(paginatedGroupUrl(prompt, urlForExistingGroups, urlForNewGroup, section, pageNumber - 1), getMessage("group.back", user));
        if (urlForNewGroup != null)
            menu.addMenuOption(urlForNewGroup, getMessage(groupKeyForMessages, "create", "option", user));

        if (GROUP_MANAGER.equals(section) && (!groupBroker.fetchGroupsWithOneCharNames(user, 2).isEmpty()))
            menu.addMenuOption(section.toPath() + "clean", getMessage(groupKeyForMessages, "clean", "option", user));

        menu.addMenuOption("start", getMessage(groupKeyForMessages, "menu", optionsKey + "back", user));

        return menu;
    }

    /**
     * @param menu        the ussd menu to add a list of groups to
     * @param nextUrl     url of the next menu in the sequence
     * @param groups      a list of groups
     * @param user        user in session
     * @return a ussd menu that has a list groups with pagnination
     */
    public USSDMenu addGroupsToMenu(USSDMenu menu, String nextUrl, List<Group> groups, User user) {
        final String formedUrl = (!nextUrl.contains("?")) ? (nextUrl + groupUidUrlEnding) : (nextUrl + "&" + groupUidParameter + "=");
        for (Group group : groups) {
            String name = (!group.hasName()) ? getMessage(groupKeyForMessages, "unnamed", "label", unnamedGroupDate.format(group.getCreatedDateTimeAtSAST()), user)
                    : group.getGroupName();
            menu.addMenuOption(formedUrl + group.getUid(), name);
        }
        return menu;
    }

    /**
     * SECTION 2: Methods to enter numbers for creating a new group, handling input, and exiting again
     */


    public USSDMenu createGroupPrompt(User user, USSDSection section, String nextUrl) throws URISyntaxException {
        log.info("Constructing prompt for new group's name, with url ... " + nextUrl);
        USSDMenu thisMenu = new USSDMenu(getMessage(section, groupKeyForMessages, promptKey + ".create", user));
        thisMenu.setFreeText(true);
        thisMenu.setNextURI(section.toPath() + nextUrl);
        return thisMenu;
    }

    public USSDMenu addNumbersToGroupPrompt(User user, Group group, USSDSection section, String nextUrl) throws URISyntaxException {
        log.info("Constructing prompt for new group's name, with url ... " + nextUrl);
        USSDMenu thisMenu = new USSDMenu(getMessage(section, groupKeyForMessages, promptKey + ".add-numbers", user));
        String groupIdWithParams = (nextUrl.contains("?")) ? ("&" + groupUidParameter + "=") : groupUidUrlEnding;
        thisMenu.setFreeText(true);
        thisMenu.setNextURI(section.toPath() + nextUrl+groupIdWithParams+group.getUid());
        return thisMenu;
    }

    public USSDMenu invalidGroupNamePrompt(User user, String groupName, USSDSection section, String nextUrl) throws URISyntaxException {
        USSDMenu thisMenu = new USSDMenu(getMessage(section, groupKeyForMessages, promptKey + ".invalid-name", groupName, user));
        thisMenu.setFreeText(true);
        thisMenu.setNextURI(section.toPath() + nextUrl);
        return thisMenu;
    }

    public USSDMenu addNumbersToExistingGroup(User user, String groupUid, USSDSection section, String userInput, String returnUrl)
            throws URISyntaxException {
        Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        groupBroker.addMembers(user.getUid(), groupUid, turnNumbersIntoMembers(enteredNumbers.get(validNumbers)), false);
        return checkForErrorsAndSetPrompt(user, section, new USSDMenu(true), groupUid, enteredNumbers.get(invalidNumbers), returnUrl);
    }

    private Set<MembershipInfo> turnNumbersIntoMembers(List<String> validNumbers) {
        Set<MembershipInfo> newMembers = new HashSet<>();
        for (String validNumber : validNumbers)
            newMembers.add(new MembershipInfo(validNumber, BaseRoles.ROLE_ORDINARY_MEMBER, null));
        return newMembers;
    }

    private USSDMenu checkForErrorsAndSetPrompt(User user, USSDSection section, USSDMenu menu, String groupUid, List<String> invalidNumbers,
                                                String returnUrl) {
        log.info("Inside checkForErrorsAndSetPrompt ... with invalid numbers ... " + invalidNumbers.toString());
        String prompt = invalidNumbers.isEmpty() ? getMessage(section, groupKeyForMessages, promptKey + ".added", user) :
                getMessage(section, groupKeyForMessages, promptKey + ".error", String.join(", ", invalidNumbers), user);
        menu.setPromptMessage(prompt);
        String groupIdWithParams = (returnUrl.contains("?")) ? ("&" + groupUidParameter + "=") : groupUidUrlEnding;
        menu.setNextURI(section.toPath() + returnUrl + groupIdWithParams + groupUid);
        return menu;
    }

    public USSDMenu existingGroupMenu(User user, String groupUid, boolean skippedSelection) {

        final Group group = groupBroker.load(groupUid);
        final String menuKey = GROUP_MANAGER.toKey() + existingGroupMenu + "." + optionsKey;

        final boolean openToken = group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime());

        USSDMenu listMenu = new USSDMenu(assembleGroupPrompt(group, user, openToken, skippedSelection));

        if (skippedSelection) {
            listMenu.addMenuOption(GROUP_MANAGER.toPath() + "create", getMessage(GROUP_MANAGER.toKey() + "create.option", user));
            if (user.hasSafetyGroup())
                listMenu.addMenuOption(SAFETY_GROUP_MANAGER.toPath() + "start", getMessage(GROUP_MANAGER.toString() + ".safety.option", user));

        }
        if (permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER))
            listMenu.addMenuOption(groupMenuWithId(addMemberPrompt, groupUid), getMessage(menuKey + addMemberPrompt, user));

        if (permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS))
            listMenu.addMenuOption(groupMenuWithId(renameGroupPrompt, groupUid), getMessage(menuKey + renameGroupPrompt, user));

        if (permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)) {
            listMenu.addMenuOption(groupMenuWithId(advancedGroupMenu, groupUid), getMessage(menuKey + advancedGroupMenu, user));
        } else {
            listMenu.addMenuOption(groupMenuWithId(unsubscribePrompt, groupUid), getMessage(menuKey + unsubscribePrompt, user));
        }

        if (user.hasSafetyGroup() && group.equals(user.getSafetyGroup())) {
            listMenu.addMenuOption(SAFETY_GROUP_MANAGER.toPath() + "start", getMessage(menuKey + "safety", user));
        }

        if (listMenu.getMenuOptions().size() < 4) {
            listMenu.addMenuOption(USSDSection.MEETINGS.toPath() + "start", getMessage(menuKey + "back-mtg", user));
        }

        listMenu.addMenuOption(skippedSelection ? "start" : GROUP_MANAGER.toPath() + "start", getMessage(menuKey + "back", user));
        // listMenu.addMenuOption("start", getMessage(groupKeyForMessages,"menu", optionsKey+"back", user));

        return listMenu;

    }

    public USSDMenu advancedGroupOptionsMenu(User user, String groupUid) {

        Group group = groupBroker.load(groupUid);
        boolean openToken = group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime());

        USSDMenu listMenu = new USSDMenu(assembleGroupPrompt(group, user, openToken, false));
        final String menuKey = GROUP_MANAGER.toKey() + advancedGroupMenu + "." + optionsKey;
        final String tokenKey = openToken ? menuKey + groupTokenMenu + ".exists" : menuKey + groupTokenMenu + ".create";

        if (permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)) {
            String visibilityMenuOptionPrompt = group.isDiscoverable() ? getMessage(menuKey + hideGroup, user)
                    : getMessage(menuKey + showGroup, user);
            listMenu.addMenuOption(groupMenuWithId(groupTokenMenu, groupUid), getMessage(tokenKey, user));
            listMenu.addMenuOption(groupMenuWithId(visibility, groupUid), visibilityMenuOptionPrompt);
        }

        if (groupBroker.isDeactivationAvailable(user, group, true))
            listMenu.addMenuOption(groupMenuWithId(inactiveMenu, groupUid), getMessage(menuKey + inactiveMenu, user));

        if (!group.getCreatedByUser().equals(user))
            listMenu.addMenuOption(groupMenuWithId(unsubscribePrompt, groupUid), getMessage(GROUP_MANAGER, existingGroupMenu, optionsKey + unsubscribePrompt, user));

        listMenu.addMenuOption(groupMenuWithId(listGroupMembers, groupUid), getMessage(menuKey + listGroupMembers, user));

        listMenu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid), getMessage(menuKey + "back", user));

        return listMenu;

    }

    private String assembleGroupPrompt(Group group, User user, boolean openToken, boolean skippedSelection) {
        String prompt;
        if (skippedSelection) {
            prompt = getMessage(GROUP_MANAGER, existingGroupMenu, promptKey + ".single", group.getName(""), user);
        } else {
            if (openToken) {
                String dial = String.format(joinCodeFormat, group.getGroupTokenCode());
                prompt = getMessage(GROUP_MANAGER, existingGroupMenu, promptKey + ".token", dial, user);
            } else {
                prompt = getMessage(GROUP_MANAGER, existingGroupMenu, promptKey, user);
            }
        }
        return prompt;

    }

    public static boolean isValidGroupName(String groupName) {
        return groupName.length() > 1;
    }


}
