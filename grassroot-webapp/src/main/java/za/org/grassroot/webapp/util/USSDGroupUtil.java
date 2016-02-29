package za.org.grassroot.webapp.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    private UserManagementService userManager;

    @Autowired
    private EventManagementService eventManager;

    @Autowired
    private LogBookService logBookService;

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

    public USSDMenu askForGroupAllowCreateNew(User sessionUser, USSDSection section, String nextUrl,
                                              String newGroupMenu, String nonGroupParams) throws URISyntaxException {

        // todo: the groupManager call is probably quite expensive, replace with a count query (non trivial, at least for me)
        USSDMenu groupMenu;
        log.info("Inside askForGroupAllowCreateNew ... newGroupUrl is ..." + newGroupMenu);
        if (!groupManager.hasActiveGroupsPartOf(sessionUser)) {
            groupMenu = createGroupPrompt(sessionUser, section, newGroupMenu);
        } else {
            String prompt = getMessage(section, groupKeyForMessages, promptKey + ".existing", sessionUser);
            String existingGroupUri = section.toPath() + nextUrl + ((nonGroupParams == null) ? "" : nonGroupParams);
            String newGroupUri = section.toPath() + newGroupMenu + ((nonGroupParams == null) ? "" : nonGroupParams);
            groupMenu = userGroupMenuPageOne(sessionUser, prompt, existingGroupUri, newGroupUri, section);
        }
        return groupMenu;
    }

    public USSDMenu askForGroupNoInlineNew(User sessionUser, USSDSection section, String promptIfExisting, String promptIfEmpty,
                                           String urlIfExisting, String urlIfEmpty, String nonGroupParams) throws URISyntaxException {
        USSDMenu groupMenu;
        // todo: replace the getter with a less expensive call (boolean query -- non-trivial), plus filter for permissions
        if (!groupManager.hasActiveGroupsPartOf(sessionUser)) {
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
        final String urlIfEmpty = USSDSection.GROUP_MANAGER.toPath() + "create";
        return askForGroupNoInlineNew(user, section, promptIfExisting, promptIfEmpty, menuIfExisting, urlIfEmpty, "");
    }

    // similar helper method
    public USSDMenu askForGroupNoInlineNew(User user, USSDSection section, String menuIfExisting, String promptIfNotEmpty) throws URISyntaxException {
        final String promptIfEmpty = getMessage(section, groupKeyForMessages, promptKey + ".empty", user);
        final String urlIfEmpty = USSDSection.GROUP_MANAGER.toPath() + "create";
        return askForGroupNoInlineNew(user, section, promptIfNotEmpty, promptIfEmpty, menuIfExisting, urlIfEmpty, "");
    }

    public USSDMenu userGroupMenuPageOne(User user, String prompt, String existingGroupUrl, String newGroupUrl, USSDSection section) throws URISyntaxException {
        return userGroupMenuPaginated(user, prompt, existingGroupUrl, newGroupUrl, 0, section);
    }

    public USSDMenu userGroupMenuPaginated(User user, String prompt, String urlForExistingGroups, String urlForNewGroup, Integer pageNumber, USSDSection section)
            throws URISyntaxException {
        USSDMenu menu = new USSDMenu(prompt);

      //todo: this is just for testing purposes
      /*  Authentication authentication = new UsernamePasswordAuthenticationToken(user, null,
                user.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.getContext();
        securityContext.setAuthentication(authentication);*/
      //  List<Group> groups = groupManager.getActiveGroupsPartOf(user);
      //  Page<Group> groupsPartOf = new PageImpl<>(groups);

       Page<Group> groupsPartOf = groupManager.getPageOfActiveGroups(user, pageNumber, PAGE_LENGTH);
        if (groupsPartOf.getTotalElements() == 1) {
            menu = skipGroupSelection(user, section,urlForNewGroup, groupsPartOf.iterator().next().getId());
        } else {
            menu = addListOfGroupsToMenu(menu, urlForExistingGroups, groupsPartOf.getContent(), user);
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
            log.info("About to create group with these numbers ... " + enteredNumbers.get(validNumbers).toString() + ".... created by this user: " + user.toString());
            Group createdGroup = groupManager.createNewGroup(user, enteredNumbers.get(validNumbers), true);
            log.info("Okay, we created this group ... " + createdGroup);
            checkForErrorsAndSetPrompt(user, section, menu, createdGroup.getId(), enteredNumbers.get(invalidNumbers), returnUrl, true);
            groupId = createdGroup.getId();
        }
        return groupId;
    }

    // note: we might think of adding a 'interrupted' check here, but addNumbersToGroup checks for duplicates anyway, and that sort of
    // thing should probably be optimized in services rather than worked around here.
    public USSDMenu addNumbersToExistingGroup(User user, Long groupId, USSDSection section, String userInput, String returnUrl)
            throws URISyntaxException {
        Map<String, List<String>> enteredNumbers = PhoneNumberUtil.splitPhoneNumbers(userInput);
        groupManager.addNumbersToGroup(groupId, enteredNumbers.get(validNumbers), user, true);
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
                menu = new USSDMenu(getMessage(section, existingGroupMenu, promptKey, sessionUser));
                String menuKey = section.toKey() + existingGroupMenu + "." + optionsKey;
                menu.addMenuOption(groupMenuWithId(groupTokenMenu, groupId), getMessage(menuKey + groupTokenMenu, sessionUser));
                menu.addMenuOption(groupMenuWithId(addMemberPrompt, groupId), getMessage(menuKey + addMemberPrompt, sessionUser));
                menu.addMenuOption(groupMenuWithId(unsubscribePrompt, groupId), getMessage(menuKey + unsubscribePrompt, sessionUser));
                menu.addMenuOption(groupMenuWithId(renameGroupPrompt, groupId), getMessage(menuKey + renameGroupPrompt, sessionUser));
                if (groupManager.isGroupCreatedByUser(groupId, sessionUser)) {
                    menu.addMenuOption(groupMenuWithId(mergeGroupMenu, groupId), getMessage(menuKey + mergeGroupMenu, sessionUser));
                }
                if (groupManager.canUserMakeGroupInactive(sessionUser, groupId)) {
                    menu.addMenuOption(groupMenuWithId(inactiveMenu, groupId), getMessage(menuKey + inactiveMenu, sessionUser));
                }
                menu.addMenuOption(urlForNewGroup, getMessage(groupKeyForMessages, "create", "option", sessionUser));
                break;
            default:
                break;

        }
        return menu;
    }



}
