package za.org.grassroot.webapp.util;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Instant;
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
    private UserManagementService userManager;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private EventRequestBroker eventRequestBroker;

    @Autowired
    private TodoRequestBroker todoRequestBroker;

    @Autowired
    PermissionBroker permissionBroker;

    @Autowired
    CacheUtilService cacheManager;

    @Autowired
    GroupJoinRequestService groupJoinRequestService;

    private static final String groupKeyForMessages = "group";

    private static final String groupUidParameter = "groupUid";
    private static final String groupUidUrlEnding = "?" + groupUidParameter + "=";

    private static final String validNumbers = "valid";
    private static final String invalidNumbers = "error";
    private static final String subjectMenu = "subject",
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

    public static final SimpleDateFormat unnamedGroupDate = new SimpleDateFormat("d MMM");

    private static final Map<USSDSection, Permission> SectionPermissionMap = ImmutableMap.of(
            USSDSection.MEETINGS, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            USSDSection.VOTES, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            USSDSection.LOGBOOK, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);

    /**
     * SECTION 1: menus to ask a user to pick a group, including via pagination
     * major todo: introduce filtering by permission into these
     */

    public USSDMenu askForGroupAllowCreateNew(User sessionUser, USSDSection section, String nextUrl, String newPrompt,
                                              String createNewGroup, String nonGroupParams) throws URISyntaxException {

        /* Two cases:
        (a) user has no groups, so just ask for one and then go to the next page (goes to newGroupMenu)
        (b) user has groups, and there are open join requests on them which the user can answer
        (c) user has groups, ask for one (goes to section + nextUrl), with option to create new (goes to section + newPrompt);
         */

        USSDMenu groupMenu;
        log.info("Inside askForGroupAllowCreateNew ... newGroupUrl is ..." + createNewGroup);
        if (!userManager.isPartOfActiveGroups(sessionUser)) {
            // case (a), ask for a name and then go to the next menu
            groupMenu = createGroupPrompt(sessionUser, section, createNewGroup);
        } else if (section.equals(USSDSection.GROUP_MANAGER) && hasOpenJoinRequests(sessionUser)) {
            // case (b), display first join request
            groupMenu = showGroupRequests(sessionUser, section);
        } else {
            // case (c), ask for an existing one, with option to prompt for new one
            String prompt = getMessage(section, groupKeyForMessages, promptKey + ".existing", sessionUser);
            String existingGroupUri = section.toPath() + nextUrl + ((nonGroupParams == null) ? "" : nonGroupParams);
            String newGroupUri = section.toPath() + newPrompt + ((nonGroupParams == null) ? "" : nonGroupParams);
            groupMenu = userGroupMenuPageOne(sessionUser, prompt, existingGroupUri, newGroupUri, section);
        }
        return groupMenu;

    }

    /**
     * @param sessionUser      currently active user
     * @param section          section of the ussd menu that is currently in view
     * @param promptIfExisting ussd prompt message to display if user belongs to one or more groups
     * @param promptIfEmpty    ussd prompt message to display when user does not belong to any group
     * @param urlIfExisting    sub url to go to next if user already belongs to a group
     * @param urlIfEmpty       sub url to go to next if user does not belong to any group
     * @return
     * @throws URISyntaxException
     */
    public USSDMenu askForGroupWithoutNewOption(User sessionUser, USSDSection section, String promptIfExisting, String promptIfEmpty,
                                                String urlIfExisting, String urlIfEmpty) throws URISyntaxException {
        USSDMenu groupMenu;
        // todo: replace this with a check on permissions
        Permission filter = SectionPermissionMap.get(section); // returning null is what we want if key not present
        GroupPage groupsPartOf = permissionBroker.getPageOfGroupDTOs(sessionUser, filter, 0, PAGE_LENGTH);
        if (groupsPartOf == null || groupsPartOf.getContent().isEmpty()) {
            groupMenu = new USSDMenu(promptIfEmpty);
            groupMenu.addMenuOption(urlIfEmpty, getMessage(section, groupKeyForMessages, "options.new", sessionUser));
            groupMenu.addMenuOption("start", getMessage("start", sessionUser));
            groupMenu.addMenuOption("exit", getMessage("exit.option", sessionUser));
        } else {
            String existingGroupUri = section.toPath() + urlIfExisting;
            groupMenu = userGroupMenuPageOne(sessionUser, promptIfExisting, existingGroupUri, null, section);
        }
        return groupMenu;
    }

    public USSDMenu showGroupRequests(User user, USSDSection section) {
        List<GroupJoinRequest> requests = groupJoinRequestService.getOpenRequestsForUser(user.getUid());
        GroupJoinRequest request = requests.get(0);
        String prompt;
        String userUid = request.getRequestor().getUid();
        String requestUid = request.getUid();
        String displayName = request.getRequestor().getDisplayName();
        String groupName = request.getGroup().getGroupName();
        prompt = getMessage(section.toString(), groupKeyForMessages, promptKey + ".join_request", new String[]{displayName, groupName}, user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(USSDUrlUtil.approveRejectRequestMenuUrl("approve", userUid, requestUid), getMessage(section, groupKeyForMessages, optionsKey + "approve", user));
        menu.addMenuOption(USSDUrlUtil.approveRejectRequestMenuUrl("reject", userUid, requestUid), getMessage(section, groupKeyForMessages, optionsKey + "reject", user));

        return menu;
    }

    // helper method which will use some defaults
    public USSDMenu askForGroupWithoutNewOption(User user, USSDSection section, String menuIfExisting) throws URISyntaxException {
        final String promptIfExisting = getMessage(section, groupKeyForMessages, promptKey, user);
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = GROUP_MANAGER.toPath() + "create";
        return askForGroupWithoutNewOption(user, section, promptIfExisting, promptIfEmpty, menuIfExisting, urlIfEmpty);
    }

    // similar helper method
    public USSDMenu askForGroupWithoutNewOption(User user, USSDSection section, String menuIfExisting, String promptIfNotEmpty) throws URISyntaxException {
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = GROUP_MANAGER.toPath() + "create";
        return askForGroupWithoutNewOption(user, section, promptIfNotEmpty, promptIfEmpty, menuIfExisting, urlIfEmpty);
    }

    public USSDMenu userGroupMenuPageOne(User user, String prompt, String existingGroupUrl, String newGroupUrl,
                                         USSDSection section) throws URISyntaxException {
        return userGroupMenuPaginated(user, prompt, existingGroupUrl, newGroupUrl, 0, section);
    }


    /**
     * @param user                 user currently in session
     * @param prompt               ussd prompt
     * @param urlForExistingGroups the url for exisitng groups
     * @param urlForNewGroup       url for new groups
     * @param pageNumber           current page
     * @param section              current section
     * @return
     * @throws URISyntaxException
     */
    public USSDMenu userGroupMenuPaginated(User user, String prompt, String urlForExistingGroups, String urlForNewGroup,
                                           Integer pageNumber, USSDSection section) throws URISyntaxException {

        USSDMenu menu = new USSDMenu(prompt);
        Permission filter = SectionPermissionMap.get(section); // returning null is what we want if key not present
        GroupPage groupsPartOf = permissionBroker.getPageOfGroupDTOs(user, filter, pageNumber, PAGE_LENGTH);
        log.debug("Getting groups for USSD menu, permission={}, found {} groups", filter, groupsPartOf.getTotalElements());

        if (groupsPartOf.getTotalElements() == 1 && section != USSDSection.MEETINGS && section != USSDSection.SAFETY_GROUP_MANAGER) { // exclude meetings and safety group since can create new in it
            menu = skipGroupSelection(user, section, groupsPartOf.getContent().get(0));
        } else {
            menu = addListOfGroupsToMenu(menu, section, urlForExistingGroups, groupsPartOf.getContent(), user);
            if (groupsPartOf.hasNext())
                menu.addMenuOption(USSDUrlUtil.paginatedGroupUrl(prompt, urlForExistingGroups, urlForNewGroup, pageNumber + 1),
                        "More groups"); // todo: i18n
            if (groupsPartOf.hasPrevious())
                menu.addMenuOption(USSDUrlUtil.paginatedGroupUrl(prompt, urlForExistingGroups, urlForNewGroup, pageNumber - 1),
                        "Back"); // todo: i18n
            if (urlForNewGroup != null)
                menu.addMenuOption(urlForNewGroup, getMessage(groupKeyForMessages, "create", "option", user));
            if (user.hasSafetyGroup() && section != null && !section.equals(SAFETY_GROUP_MANAGER))
                menu.addMenuOption(SAFETY_GROUP_MANAGER.toPath() + "start", getMessage(GROUP_MANAGER.toString() + ".safety.option", user));
        }

        if (USSDSection.GROUP_MANAGER.equals(section) && (!groupBroker.fetchGroupsWithOneCharNames(user, 2).isEmpty())) {
            menu.addMenuOption(section.toPath() + "clean", getMessage(groupKeyForMessages, "clean", "option", user));
        }
        menu.addMenuOption("start", getMessage(groupKeyForMessages,"menu", optionsKey +"back", user));

        return menu;
    }

    /**
     * @param menu        the ussd menu to add a list of groups to
     * @param nextMenuUrl url of the next menu in the sequence
     * @param groups      a list of groups
     * @param user        user in session
     * @return a ussd menu that has a list groups with pagnination
     */
    public USSDMenu addListOfGroupsToMenu(USSDMenu menu, String nextMenuUrl, List<Group> groups, User user) {
        final String formedUrl = (!nextMenuUrl.contains("?")) ?
                (nextMenuUrl + groupUidUrlEnding) :
                (nextMenuUrl + "&" + groupUidParameter + "=");
        for (Group group : groups) {
            String name = (group.hasName()) ? group.getGroupName() :
                    getMessage(groupKeyForMessages, "unnamed", "label", unnamedGroupDate.format(group.getCreatedDateTime()), user);
            menu.addMenuOption(formedUrl + group.getUid(), name);
        }
        return menu;
    }

    public USSDMenu addListOfGroupsToMenu(USSDMenu menu, USSDSection section, String nextMenuUrl, List<GroupDTO> groups,
                                          User user) {

        final String formedUrl = (!nextMenuUrl.contains("?")) ?
                (nextMenuUrl + groupUidUrlEnding) :
                (nextMenuUrl + "&" + groupUidParameter + "=");

        for (GroupDTO group : groups) {
            String name = (group.hasName()) ? group.getGroupName() :
                    getMessage(groupKeyForMessages, "unnamed", "label", unnamedGroupDate.format(group.getCreatedDateTime()), user);
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

    public USSDMenu invalidGroupNamePrompt(User user, String groupName, USSDSection section, String nextUrl) throws URISyntaxException {
        USSDMenu thisMenu = new USSDMenu(getMessage(section, groupKeyForMessages, promptKey + ".invalid-name", groupName, user));
        thisMenu.setFreeText(true);
        thisMenu.setNextURI(section.toPath() + nextUrl);
        return thisMenu;
    }

    public String addNumbersToNewGroup(User user, USSDSection section, USSDMenu menu, String userInput, String returnUrl) throws URISyntaxException {
        String groupUid;
        final Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        log.info("addNumbersToNewGroup ... with user input ... " + userInput);

        if (enteredNumbers.get(validNumbers).isEmpty()) {
            menu.setPromptMessage(getMessage(section, groupKeyForMessages, promptKey + ".error",
                    String.join(", ", enteredNumbers.get(invalidNumbers)), user));
            menu.setNextURI(section.toPath() + returnUrl);
            groupUid = "";
        } else {
            Set<MembershipInfo> members = turnNumbersIntoMembers(enteredNumbers.get(validNumbers));
            members.add(new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
            log.info("ZOGG : In GroupUtil ... Calling create with members ... " + members);
            Group createdGroup = groupBroker.create(user.getUid(), "", null, members, GroupPermissionTemplate.DEFAULT_GROUP, null, null, false);
            checkForErrorsAndSetPrompt(user, section, menu, createdGroup.getUid(), enteredNumbers.get(invalidNumbers), returnUrl, true);
            groupUid = createdGroup.getUid();
        }
        return groupUid;
    }

    private Set<MembershipInfo> turnNumbersIntoMembers(List<String> validNumbers) {
        Set<MembershipInfo> newMembers = new HashSet<>();
        for (String validNumber : validNumbers)
            newMembers.add(new MembershipInfo(validNumber, BaseRoles.ROLE_ORDINARY_MEMBER, null));
        return newMembers;
    }

    public USSDMenu addNumbersToExistingGroup(User user, String groupUid, USSDSection section, String userInput, String returnUrl)
            throws URISyntaxException {

        Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        groupBroker.addMembers(user.getUid(), groupUid, turnNumbersIntoMembers(enteredNumbers.get(validNumbers)), false);
        return checkForErrorsAndSetPrompt(user, section, new USSDMenu(true), groupUid, enteredNumbers.get(invalidNumbers), returnUrl, false);
    }


    private USSDMenu checkForErrorsAndSetPrompt(User user, USSDSection section, USSDMenu menu, String groupUid, List<String> invalidNumbers,
                                                String returnUrl, boolean newGroup) {
        log.info("Inside checkForErrorsAndSetPrompt ... with invalid numbers ... " + invalidNumbers.toString());
        String addedOrCreated = newGroup ? ".created" : ".added";
        String prompt = invalidNumbers.isEmpty() ? getMessage(section, groupKeyForMessages, promptKey + addedOrCreated, user) :
                getMessage(section, groupKeyForMessages, promptKey + ".error", String.join(", ", invalidNumbers), user);
        menu.setPromptMessage(prompt);
        String groupIdWithParams = (returnUrl.contains("?")) ? ("&" + groupUidParameter + "=") : groupUidUrlEnding;
        menu.setNextURI(section.toPath() + returnUrl + groupIdWithParams + groupUid);
        return menu;
    }

    private USSDMenu skipGroupSelection(User user, USSDSection section, GroupDTO group) {
        USSDMenu menu = null;
        String nextUrl;
        switch (section) {
            case VOTES:
                VoteRequest voteRequest = eventRequestBroker.createEmptyVoteRequest(user.getUid(), group.getUid());
                String requestUid = voteRequest.getUid();
                cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveVoteMenu("issue", requestUid));
                nextUrl = "vote/time" + USSDUrlUtil.entityUidUrlSuffix + requestUid;
                menu = new USSDMenu(getMessage(section, "issue", promptKey + ".skipped", group.getDisplayName(""), user), nextUrl);
                break;
            case LOGBOOK:
                String logBookUid = todoRequestBroker.create(user.getUid(), group.getUid()).getUid();
                cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveLogMenu(subjectMenu, logBookUid));
                nextUrl = "log/due_date" + USSDUrlUtil.logbookIdUrlSuffix + logBookUid;
                menu = new USSDMenu(getMessage(section, subjectMenu, promptKey + ".skipped", group.getDisplayName(""), user), nextUrl);
                break;
            case GROUP_MANAGER:
                menu = existingGroupMenu(user, group.getUid(), true);
                break;
            default:
                break;

        }
        return menu;
    }

    public USSDMenu existingGroupMenu(User user, String groupUid, boolean skippedSelection) {

        final Group group = groupBroker.load(groupUid);
        final String menuKey = GROUP_MANAGER.toKey() + existingGroupMenu + "." + optionsKey;

        final boolean openToken = group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime().toInstant());

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
            listMenu.addMenuOption(SAFETY_GROUP_MANAGER.toPath() + "start", getMessage(GROUP_MANAGER.toString() + ".safety.option", user));
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
        boolean openToken = group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime().toInstant());

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
                String dial = "*134*1994*" + group.getGroupTokenCode() + "#";
                prompt = getMessage(GROUP_MANAGER, existingGroupMenu, promptKey + ".token", dial, user);
            } else {
                prompt = getMessage(GROUP_MANAGER, existingGroupMenu, promptKey, user);
            }
        }

        return prompt;

    }

    private boolean hasOpenJoinRequests(User user) {
        return groupJoinRequestService.getOpenRequestsForUser(user.getUid()).size() > 0;
    }


    public static boolean isValidGroupName(String groupName) {
        return groupName.length() > 1;
    }


}
