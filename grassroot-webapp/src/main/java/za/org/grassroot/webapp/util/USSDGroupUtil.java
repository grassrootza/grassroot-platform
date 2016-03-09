package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private GroupManagementService groupManager;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private UserManagementService userManager;

    @Autowired
    private EventManagementService eventManager;

    @Autowired
    private LogBookService logBookService;

    @Autowired
    GroupAccessControlManagementService groupAccessControlManager;

    private static final String groupKeyForMessages = "group";
    private static final String groupIdParameter = "groupId";
    private static final String groupIdUrlEnding = "?" + groupIdParameter + "=";
    private static final String validNumbers = "valid";
    private static final String invalidNumbers = "error";
    private static final String subjectMenu = "subject",
            placeMenu = "place", existingGroupMenu = "menu", timeMenu = "time",
            unsubscribePrompt = "unsubscribe",
            groupTokenMenu = "token", renameGroupPrompt = "rename",
            addMemberPrompt = "addnumber", mergeGroupMenu = "merge",
            inactiveMenu = "inactive";

    private static final SimpleDateFormat unnamedGroupDate = new SimpleDateFormat("d MMM");

    /**
     * SECTION 1: menus to ask a user to pick a group, including via pagination
     * major todo: introduce filtering by permission into these
     */

    public USSDMenu askForGroupAllowCreateNew(User sessionUser, USSDSection section, String nextUrl, String newPrompt,
                                              String createNewGroup, String nonGroupParams, boolean checkPermissions) throws URISyntaxException {

        /* Two cases:
        (a) user has no groups, so just ask for one and then go to the next page (goes to newGroupMenu)
        (b) user has groups, ask for one (goes to section + nextUrl), with option to create new (goes to section + newPrompt);
         */

        USSDMenu groupMenu;
        log.info("Inside askForGroupAllowCreateNew ... newGroupUrl is ..." + createNewGroup);

        if (!groupManager.hasActiveGroupsPartOf(sessionUser)) {
            // case (b), ask for a name and then go to the next menu
            groupMenu = createGroupPrompt(sessionUser, section, createNewGroup);
        } else {
            // case (a), ask for an existing one, with option to prompt for new one
            String prompt = getMessage(section, groupKeyForMessages, promptKey + ".existing", sessionUser);
            String existingGroupUri = section.toPath() + nextUrl + ((nonGroupParams == null) ? "" : nonGroupParams);
            String newGroupUri = section.toPath() + newPrompt + ((nonGroupParams == null) ? "" : nonGroupParams);
            groupMenu = userGroupMenuPageOne(sessionUser, prompt, existingGroupUri, newGroupUri, section, checkPermissions);
        }
        return groupMenu;
    }

    public USSDMenu askForGroupNoInlineNew(User sessionUser, USSDSection section, String promptIfExisting, String promptIfEmpty,
                                           String urlIfExisting, String urlIfEmpty, String nonGroupParams, boolean checkPermissions) throws URISyntaxException {
        USSDMenu groupMenu;
        // todo: replace the getter with a less expensive call (boolean query -- non-trivial), plus filter for permissions
        if (!groupManager.hasActiveGroupsPartOf(sessionUser)) {
            groupMenu = new USSDMenu(promptIfEmpty);
            groupMenu.addMenuOption(urlIfEmpty, getMessage(section, groupKeyForMessages, "options.new", sessionUser));
            groupMenu.addMenuOption("start", getMessage("start", sessionUser));
            groupMenu.addMenuOption("exit", getMessage("exit.option", sessionUser));
        } else {
            String existingGroupUri = section.toPath() + urlIfExisting + ((nonGroupParams == null) ? "" : nonGroupParams);
            groupMenu = userGroupMenuPageOne(sessionUser, promptIfExisting, existingGroupUri, null, section, checkPermissions);
        }
        return groupMenu;
    }

    // helper method which will use some defaults
    public USSDMenu askForGroupNoInlineNew(User user, USSDSection section, String menuIfExisting) throws URISyntaxException {
        final String promptIfExisting = getMessage(section, groupKeyForMessages, promptKey, user);
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = USSDSection.GROUP_MANAGER.toPath() + "create";
        return askForGroupNoInlineNew(user, section, promptIfExisting, promptIfEmpty, menuIfExisting, urlIfEmpty, "", false);
    }

    // similar helper method
    public USSDMenu askForGroupNoInlineNew(User user, USSDSection section, String menuIfExisting, String promptIfNotEmpty) throws URISyntaxException {
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = USSDSection.GROUP_MANAGER.toPath() + "create";
        return askForGroupNoInlineNew(user, section, promptIfNotEmpty, promptIfEmpty, menuIfExisting, urlIfEmpty, "", false);
    }

    public USSDMenu userGroupMenuPageOne(User user, String prompt, String existingGroupUrl, String newGroupUrl,
                                         USSDSection section, boolean checkPermissions) throws URISyntaxException {
        return userGroupMenuPaginated(user, prompt, existingGroupUrl, newGroupUrl, 0, section, checkPermissions);
    }

    public USSDMenu userGroupMenuPaginated(User user, String prompt, String urlForExistingGroups, String urlForNewGroup,
                                           Integer pageNumber, USSDSection section, boolean checkPermissions)
            throws URISyntaxException {

        USSDMenu menu = new USSDMenu(prompt);

        Page<Group> groupsPartOf = groupManager.getPageOfActiveGroups(user, pageNumber, PAGE_LENGTH);
        if (groupsPartOf.getTotalElements() == 1 && section != USSDSection.MEETINGS) { // exclude meetings since can create new in it
            menu = skipGroupSelection(user, section,urlForNewGroup, groupsPartOf.iterator().next().getId());
        } else {
            menu = addListOfGroupsToMenu(menu,section, urlForExistingGroups, groupsPartOf.getContent(), user, checkPermissions);
            if (groupsPartOf.hasNext())
                menu.addMenuOption(USSDUrlUtil.paginatedGroupUrl(prompt, urlForExistingGroups, urlForNewGroup, pageNumber + 1),
                        "More groups"); // todo: i18n
            if (groupsPartOf.hasPrevious())
                menu.addMenuOption(USSDUrlUtil.paginatedGroupUrl(prompt, urlForExistingGroups, urlForNewGroup, pageNumber - 1),
                        "Back"); // todo: i18n
            if (urlForNewGroup != null)
                menu.addMenuOption(urlForNewGroup, getMessage(groupKeyForMessages, "create", "option", user));
        }
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

    public USSDMenu addListOfGroupsToMenu(USSDMenu menu,USSDSection section, String nextMenuUrl, List<Group> groups,
                                          User user, boolean checkPerm ) {

        final String formedUrl = (!nextMenuUrl.contains("?")) ?
                (nextMenuUrl + groupIdUrlEnding) :
                (nextMenuUrl + "&" + groupIdParameter + "=");

        for (Group group : groups) {

            String name = (group.hasName()) ? group.getGroupName() :
                    getMessage(groupKeyForMessages, "unnamed", "label", unnamedGroupDate.format(group.getCreatedDateTime()), user);

            menu.addMenuOption(formedUrl + group.getId(), name);

            if (checkPerm) {
                if (hasPermission(section, group, user)) {
                    menu.addMenuOption(formedUrl + group.getId(), name);
                }
            } else {
                menu.addMenuOption(formedUrl + group.getId(), name);
            }
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

    public Long addNumbersToNewGroup(User user, USSDSection section, USSDMenu menu, String userInput, String returnUrl) throws URISyntaxException {
        Long groupId;
        final Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        log.info("addNumbersToNewGroup ... with user input ... " + userInput);

        if (enteredNumbers.get(validNumbers).isEmpty()) {

            menu.setPromptMessage(getMessage(section, groupKeyForMessages, promptKey + ".error",
                    String.join(", ", enteredNumbers.get(invalidNumbers)), user));
            menu.setNextURI(section.toPath() + returnUrl);
            groupId = 0L;

        } else {

            Set<MembershipInfo> members = turnNumbersIntoMembers(enteredNumbers.get(validNumbers));
            members.add(new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
            log.info("ZOGG : In GroupUtil ... Calling create with members ... " + members);
            Group createdGroup = groupBroker.create(user.getUid(), "", null, members, GroupPermissionTemplate.DEFAULT_GROUP);
            checkForErrorsAndSetPrompt(user, section, menu, createdGroup.getId(), enteredNumbers.get(invalidNumbers), returnUrl, true);
            groupId = createdGroup.getId();

        }
        return groupId;
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
        groupBroker.addMembers(user.getUid(), groupUid, turnNumbersIntoMembers(enteredNumbers.get(validNumbers)));
        Long groupId = groupManager.loadGroupByUid(groupUid).getId(); // temp, until transitioned all to Uid
        return checkForErrorsAndSetPrompt(user, section, new USSDMenu(true), groupId, enteredNumbers.get(invalidNumbers), returnUrl, false);
    }

    private USSDMenu checkForErrorsAndSetPrompt(User user, USSDSection section, USSDMenu menu, Long groupId, List<String> invalidNumbers,
                                                String returnUrl, boolean newGroup) {
        log.info("Inside checkForErrorsAndSetPrompt ... with invalid numbers ... " + invalidNumbers.toString());
        String addedOrCreated = newGroup ? ".created" : ".added";
        String prompt = invalidNumbers.isEmpty() ? getMessage(section, groupKeyForMessages, promptKey + addedOrCreated, user) :
                getMessage(section, groupKeyForMessages, promptKey + ".error", String.join(", ", invalidNumbers), user);
        menu.setPromptMessage(prompt);
        String groupIdWithParams = (returnUrl.contains("?")) ? ("&" + groupIdParameter + "=") : groupIdUrlEnding;
        menu.setNextURI(section.toPath() + returnUrl + groupIdWithParams + groupId);
        return menu;
    }

    public USSDMenu skipGroupSelection(User sessionUser, USSDSection section, String urlForNewGroup, Long groupId) {
        USSDMenu menu = null;
        String nextUrl;
        switch (section) {
            case MEETINGS:
                Long eventId = eventManager.createMeeting(sessionUser.getPhoneNumber(), groupId).getId();
                sessionUser = userManager.findByInputNumber(sessionUser.getPhoneNumber(), saveMeetingMenu(subjectMenu, eventId, false));
                String promptMessage = getMessage(section, subjectMenu, promptKey, sessionUser);
                nextUrl = "mtg/" + "place" + USSDUrlUtil.eventIdUrlSuffix + eventId + "&prior_menu=subject";
                menu = new USSDMenu(promptMessage, nextUrl);
                break;
            case VOTES:
                sessionUser = userManager.findByInputNumber(sessionUser.getPhoneNumber());
                eventId = eventManager.createVote(sessionUser, groupId).getId();
                userManager.setLastUssdMenu(sessionUser, saveVoteMenu("issue", eventId));
                nextUrl = "vote/" + "time" + USSDUrlUtil.eventIdUrlSuffix + eventId;
                menu = new USSDMenu(getMessage(section, "issue", promptKey, sessionUser), nextUrl);
                break;
            case LOGBOOK:
                User user = userManager.findByInputNumber(sessionUser.getPhoneNumber());
                Long logBookId = logBookService.create(user.getId(), groupId, false).getId();
                userManager.setLastUssdMenu(user, saveLogMenu(subjectMenu, logBookId));
                nextUrl = "log/due_date" + USSDUrlUtil.logbookIdUrlSuffix + logBookId;
                menu = new USSDMenu(getMessage(section, subjectMenu, promptKey, user), nextUrl);
                break;
            case GROUP_MANAGER:
                sessionUser = userManager.findByInputNumber(sessionUser.getPhoneNumber(), saveGroupMenu(existingGroupMenu, groupId));
                Group group = groupManager.loadGroup(groupId);
                menu = new USSDMenu(getMessage(section, existingGroupMenu, promptKey, sessionUser));
                String menuKey = section.toKey() + existingGroupMenu + "." + optionsKey;
                menu.addMenuOption(urlForNewGroup, getMessage(groupKeyForMessages, "create", "option", sessionUser));
                menu.addMenuOption(groupMenuWithId(groupTokenMenu, groupId), getMessage(menuKey + groupTokenMenu, sessionUser));
                menu.addMenuOption(groupMenuWithId(addMemberPrompt, groupId), getMessage(menuKey + addMemberPrompt, sessionUser));
                menu.addMenuOption(groupMenuWithId(unsubscribePrompt, groupId), getMessage(menuKey + unsubscribePrompt, sessionUser));
                menu.addMenuOption(groupMenuWithId(renameGroupPrompt, groupId), getMessage(menuKey + renameGroupPrompt, sessionUser));
                if (group.getCreatedByUser().equals(sessionUser)) {
                    menu.addMenuOption(groupMenuWithId(mergeGroupMenu, groupId), getMessage(menuKey + mergeGroupMenu, sessionUser));
                }
                if (groupBroker.isDeactivationAvailable(sessionUser, group)) {
                    menu.addMenuOption(groupMenuWithId(inactiveMenu, groupId), getMessage(menuKey + inactiveMenu, sessionUser));
                }
                break;
            default:
                break;

        }
        return menu;
    }

    private boolean hasPermission(USSDSection section, Group group, User user){

        boolean canCreate = true;
        Long startTime = System.currentTimeMillis();
        switch (section){
            case MEETINGS:
                canCreate = groupAccessControlManager.hasGroupPermission(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING, group,user);
            break;
            case VOTES:
                canCreate =groupAccessControlManager.hasGroupPermission(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, group,user);
                break;
            case LOGBOOK:
                canCreate = groupAccessControlManager.hasGroupPermission(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,group,user);
                break;
            case GROUP_MANAGER:
                canCreate = groupAccessControlManager.hasGroupPermission(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,group,user);
                break;
            default:
                break;
         }
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Ussd group permission checking ... took %d msec", endTime - startTime));
        return canCreate;
    }



}
