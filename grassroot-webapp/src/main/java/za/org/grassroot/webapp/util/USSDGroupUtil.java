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
import za.org.grassroot.services.util.CacheUtilManager;
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
    private LogBookRequestBroker logBookRequestBroker;

    @Autowired
    PermissionBroker permissionBroker;

    @Autowired
    CacheUtilService cacheManager;

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
            addMemberPrompt = "addnumber",
            inactiveMenu = "inactive";

    private static final SimpleDateFormat unnamedGroupDate = new SimpleDateFormat("d MMM");

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
        (b) user has groups, ask for one (goes to section + nextUrl), with option to create new (goes to section + newPrompt);
         */

        USSDMenu groupMenu;
        log.info("Inside askForGroupAllowCreateNew ... newGroupUrl is ..." + createNewGroup);

        if (!userManager.isPartOfActiveGroups(sessionUser)) {
            // case (b), ask for a name and then go to the next menu
            groupMenu = createGroupPrompt(sessionUser, section, createNewGroup);
        } else {
            // case (a), ask for an existing one, with option to prompt for new one
            String prompt = getMessage(section, groupKeyForMessages, promptKey + ".existing", sessionUser);
            String existingGroupUri = section.toPath() + nextUrl + ((nonGroupParams == null) ? "" : nonGroupParams);
            String newGroupUri = section.toPath() + newPrompt + ((nonGroupParams == null) ? "" : nonGroupParams);
            groupMenu = userGroupMenuPageOne(sessionUser, prompt, existingGroupUri, newGroupUri, section);
        }
        return groupMenu;
    }

    public USSDMenu askForGroupNoInlineNew(User sessionUser, USSDSection section, String promptIfExisting, String promptIfEmpty,
                                           String urlIfExisting, String urlIfEmpty, String nonGroupParams) throws URISyntaxException {
        USSDMenu groupMenu;
        // todo: replace this with a check on permissions
        if (!userManager.isPartOfActiveGroups(sessionUser)) {
            groupMenu = new USSDMenu(promptIfEmpty);
            groupMenu.addMenuOption(urlIfEmpty, getMessage(section, groupKeyForMessages, "options.new", sessionUser));
            groupMenu.addMenuOption("start", getMessage("start", sessionUser));
            groupMenu.addMenuOption("exit", getMessage("exit.option", sessionUser));
        } else {
            String existingGroupUri = section.toPath() + urlIfExisting + ((nonGroupParams == null) ? "" : nonGroupParams);
            groupMenu = userGroupMenuPageOne(sessionUser, promptIfExisting, existingGroupUri, null, section);
        }
        return groupMenu;
    }

    // helper method which will use some defaults
    public USSDMenu askForGroupNoInlineNew(User user, USSDSection section, String menuIfExisting) throws URISyntaxException {
        final String promptIfExisting = getMessage(section, groupKeyForMessages, promptKey, user);
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = GROUP_MANAGER.toPath() + "create";
        return askForGroupNoInlineNew(user, section, promptIfExisting, promptIfEmpty, menuIfExisting, urlIfEmpty, "");
    }

    // similar helper method
    public USSDMenu askForGroupNoInlineNew(User user, USSDSection section, String menuIfExisting, String promptIfNotEmpty) throws URISyntaxException {
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = GROUP_MANAGER.toPath() + "create";
        return askForGroupNoInlineNew(user, section, promptIfNotEmpty, promptIfEmpty, menuIfExisting, urlIfEmpty, "");
    }

    public USSDMenu userGroupMenuPageOne(User user, String prompt, String existingGroupUrl, String newGroupUrl,
                                         USSDSection section) throws URISyntaxException {
        return userGroupMenuPaginated(user, prompt, existingGroupUrl, newGroupUrl, 0, section);
    }

    public USSDMenu userGroupMenuPaginated(User user, String prompt, String urlForExistingGroups, String urlForNewGroup,
                                           Integer pageNumber, USSDSection section)
            throws URISyntaxException {

        USSDMenu menu = new USSDMenu(prompt);

        Permission filter = SectionPermissionMap.get(section); // returning null is what we want if key not present
        GroupPage groupsPartOf = permissionBroker.getPageOfGroupDTOs(user, filter, pageNumber, PAGE_LENGTH);
        log.info("Getting groups for USSD menu, permission={}, found {} groups", filter, groupsPartOf.getTotalElements());

        if (groupsPartOf.getTotalElements() == 1 && section != USSDSection.MEETINGS) { // exclude meetings since can create new in it
            menu = skipGroupSelection(user, section, groupsPartOf.getContent().get(0));
        } else {
            menu = addListOfGroupsToMenu(menu,section, urlForExistingGroups, groupsPartOf.getContent(), user);
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
            Group createdGroup = groupBroker.create(user.getUid(), "", null, members, GroupPermissionTemplate.DEFAULT_GROUP, null, null);
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
        groupBroker.addMembers(user.getUid(), groupUid, turnNumbersIntoMembers(enteredNumbers.get(validNumbers)));
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
                menu = new USSDMenu(getMessage(section, "issue", promptKey, user), nextUrl);
                break;
            case LOGBOOK:
                String logBookUid = logBookRequestBroker.create(user.getUid(), group.getUid()).getUid();
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

    public USSDMenu existingGroupMenu(User sessionUser, String groupUid, boolean skippedSelection) {

        USSDSection thisSection = GROUP_MANAGER;
        Group group = groupBroker.load(groupUid);
        String menuKey = thisSection.toKey() + existingGroupMenu + "." + optionsKey;

        boolean openToken = group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime().toInstant());

        String tokenKey = openToken ? menuKey + groupTokenMenu + ".exists" : menuKey + groupTokenMenu + ".create";
        String prompt;

        if (skippedSelection) {
            prompt = getMessage(thisSection, existingGroupMenu, promptKey + ".single", group.getName(""), sessionUser);
        } else {
            if (openToken) {
                String dial = "*134*1994*" + group.getGroupTokenCode() + "#";
                prompt = getMessage(thisSection, existingGroupMenu, promptKey + ".token", dial, sessionUser);
            } else {
                prompt = getMessage(thisSection, existingGroupMenu, promptKey, sessionUser);
            }
        }

        USSDMenu listMenu = new USSDMenu(prompt);

        if (skippedSelection)
            listMenu.addMenuOption(GROUP_MANAGER.toPath() + "create", getMessage(GROUP_MANAGER.toKey() + "create.option", sessionUser));

        if (permissionBroker.isGroupPermissionAvailable(sessionUser, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER))
            listMenu.addMenuOption(groupMenuWithId(addMemberPrompt, groupUid), getMessage(menuKey + addMemberPrompt, sessionUser));

        if (permissionBroker.isGroupPermissionAvailable(sessionUser, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS))
            listMenu.addMenuOption(groupMenuWithId(groupTokenMenu, groupUid), getMessage(tokenKey, sessionUser));

        listMenu.addMenuOption(groupMenuWithId(unsubscribePrompt, groupUid), getMessage(menuKey + unsubscribePrompt, sessionUser));

        if (permissionBroker.isGroupPermissionAvailable(sessionUser, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS))
            listMenu.addMenuOption(groupMenuWithId(renameGroupPrompt, groupUid), getMessage(menuKey + renameGroupPrompt, sessionUser));

        if (groupBroker.isDeactivationAvailable(sessionUser, group, true))
            listMenu.addMenuOption(groupMenuWithId(inactiveMenu, groupUid), getMessage(menuKey + inactiveMenu, sessionUser));

        return listMenu;

    }

    private boolean hasPermission(USSDSection section, Group group, User user){

        boolean canCreate = true;
        Long startTime = System.currentTimeMillis();
        switch (section){
            case MEETINGS:
                canCreate = permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING);
            break;
            case VOTES:
                canCreate = permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
                break;
            case LOGBOOK:
                canCreate = permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
                break;
            case GROUP_MANAGER:
                canCreate = permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
                break;
            default:
                break;
         }
        Long endTime = System.currentTimeMillis();
        log.info(String.format("Ussd group permission checking ... took %d msec", endTime - startTime));
        return canCreate;
    }



}
