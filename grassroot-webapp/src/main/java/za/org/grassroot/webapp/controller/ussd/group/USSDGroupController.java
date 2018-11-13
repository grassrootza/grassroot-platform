package za.org.grassroot.webapp.controller.ussd.group;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.ussd.USSDBaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.enums.USSDSection.SAFETY_GROUP_MANAGER;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * @author luke on 2015/08/14.
 */
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDGroupController extends USSDBaseController {

    @Value("${grassroot.ussd.location.enabled:false}")
    private boolean locationRequestEnabled;

    private final GroupBroker groupBroker;
    private final PermissionBroker permissionBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final GroupJoinRequestService groupJoinRequestService;

    @Setter private USSDGroupUtil ussdGroupUtil;

    private static final String
            existingGroupMenu = "menu",
            advancedGroupMenu = "advanced",
            createGroupMenu = "create",
            closeGroupToken = "create-token",
            createGroupAddNumbers = "add-numbers",
            approveUser = "approve",
            rejectUser = "reject",
            addMemberPrompt = "addnumber", // probably should rename this to prevent confusion w/ above
            unsubscribePrompt = "unsubscribe";

    private static final String groupPath = homePath + groupMenus;
    private static final USSDSection thisSection = USSDSection.GROUP_MANAGER;

    private static final String groupUidParam = "groupUid";

    @Autowired
    public USSDGroupController(GroupBroker groupBroker, PermissionBroker permissionBroker, GeoLocationBroker geoLocationBroker, GroupJoinRequestService groupJoinRequestService, USSDGroupUtil ussdGroupUtil) {
        this.groupBroker = groupBroker;
        this.permissionBroker = permissionBroker;
        this.geoLocationBroker = geoLocationBroker;
        this.groupJoinRequestService = groupJoinRequestService;
        this.ussdGroupUtil = ussdGroupUtil;
    }

    /*
    Pagination helper
     */
    @RequestMapping(value = homePath + "group_page", method = RequestMethod.GET)
    @ResponseBody
    public Request groupPaginationHelper(@RequestParam(value = phoneNumber) String inputNumber,
                                         @RequestParam(value = "prompt") String prompt,
                                         @RequestParam(value = "page") Integer pageNumber,
                                         @RequestParam(value = "existingUri") String existingUri,
                                         @RequestParam(value = "section", required = false) USSDSection section,
                                         @RequestParam(value = "newUri", required = false) String newUri) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        if (SAFETY_GROUP_MANAGER.equals(section)) {
            return menuBuilder(ussdGroupUtil.showUserCreatedGroupsForSafetyFeature(user, SAFETY_GROUP_MANAGER, existingUri, pageNumber));
        } else {
            return menuBuilder(ussdGroupUtil.userGroupMenuPaginated(user, prompt, existingUri, newUri, pageNumber, null, section));
        }
    }


    /*
    First menu: display a list of groups, with the option to create a new one
    */
    @RequestMapping(value = groupPath + startMenu)
    @ResponseBody
    public Request groupList(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                             @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

        // in case went "back" from menu in middle of create group
        User user = (interrupted) ? userManager.findByInputNumber(inputNumber, null) : userManager.findByInputNumber(inputNumber);
        if (!groupJoinRequestService.getPendingRequestsForUser(user.getUid()).isEmpty()) {
            return menuBuilder(ussdGroupUtil.showGroupJoinRequests(user, USSDSection.GROUP_MANAGER));
        } else {
            final int numberGroups = permissionBroker.countActiveGroupsWithPermission(user, null);
            if (numberGroups != 1) {
                USSDGroupUtil.GroupMenuBuilder builder = new USSDGroupUtil.GroupMenuBuilder(user, thisSection);
                builder.urlForExistingGroup(existingGroupMenu)
                        .urlForCreateNewGroupPrompt(createGroupMenu)
                        .urlToCreateNewGroup(createGroupMenu + doSuffix)
                        .urlForNoGroups(createGroupMenu)
                        .urlForSendAllGroupJoinCodes("sendall")
                        .numberOfGroups(numberGroups);
                return menuBuilder(ussdGroupUtil.askForGroup(builder));
            } else {
                final String groupUid = permissionBroker.getActiveGroupsWithPermission(user, null).iterator().next().getUid();
                return menuBuilder(ussdGroupUtil.existingGroupMenu(user, groupUid, true));
            }
        }
    }

    /*
    Second menu: once the user has selected a group, give them options to name, create join code, add a member, or unsub themselves
     */

    @RequestMapping(value = groupPath + existingGroupMenu)
    @ResponseBody
    public Request groupMenu(@RequestParam(value = phoneNumber) String inputNumber,
                             @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        return (groupUid == null || groupUid.equals("")) ? createPrompt(inputNumber) :
                menuBuilder(ussdGroupUtil.existingGroupMenu(userManager.findByInputNumber(inputNumber), groupUid, false));
    }

    @RequestMapping(value = groupPath + advancedGroupMenu)
    @ResponseBody
    public Request advancedGroupMenu(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        return menuBuilder(ussdGroupUtil.advancedGroupOptionsMenu(userManager.findByInputNumber(inputNumber), groupUid));
    }

    /*
    The user is creating a group. First, ask for the group name.
     */

    @RequestMapping(value = groupPath + createGroupMenu)
    @ResponseBody
    public Request createPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber, groupMenus + createGroupMenu);
        return menuBuilder(ussdGroupUtil.createGroupPrompt(sessionUser, thisSection, createGroupMenu + doSuffix));
    }

    /*
    The user has given a name, now ask whether to enter numbers or just go straight to a joining code
     */

    @RequestMapping(value = groupPath + createGroupMenu + doSuffix)
    @ResponseBody
    public Request createGroupWithName(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                       @RequestParam(value = userInputParam, required = true) String groupName,
                                       @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                       @RequestParam(value = groupUidParam, required = false) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        Group createdGroup;
        USSDMenu menu;
        if (!isValidGroupName(groupName)) {
            cacheManager.putUssdMenuForUser(inputNumber, groupMenus + createGroupMenu);
            menu = ussdGroupUtil.invalidGroupNamePrompt(user, groupName, thisSection, createGroupMenu + doSuffix);
        } else {
            if (interrupted) {
                createdGroup = groupBroker.load(groupUid);
            } else {
                MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
                createdGroup = groupBroker.create(user.getUid(), groupName, null, Collections.singleton(creator),
                        GroupPermissionTemplate.DEFAULT_GROUP, null, null, true, false, true);
            }

            cacheManager.putUssdMenuForUser(inputNumber, saveGroupMenuWithInput(createGroupMenu + doSuffix, createdGroup.getUid(), groupName, false));

            if (!locationRequestEnabled) {
                menu = postCreateOptionsNoLocation(createdGroup.getUid(), groupName, createdGroup.getGroupTokenCode(), user);
            } else {
                menu = postCreateOptionsWithLocation(createdGroup.getUid(), createdGroup.getGroupTokenCode(), user);
            }
        }
        return menuBuilder(menu);
    }

    private USSDMenu postCreateOptionsNoLocation(final String groupUid, final String groupName, final String joiningCode, User user) {
        USSDMenu menu = new USSDMenu(getMessage(thisSection, createGroupMenu + doSuffix, promptKey,
                new String[]{groupName, joiningCode}, user));

        menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, groupUid),
                getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "numbers", user));
        menu.addMenuOption(groupMenuWithId(closeGroupToken, groupUid),
                getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "token", user));
        menu.addMenuOption(groupMenuWithId("sendall",groupUid),
                getMessage(thisSection,"sendcode", promptKey, user));

        return menu;
    }

    private USSDMenu postCreateOptionsWithLocation(final String groupUid, final String joiningCode, User user) {
        USSDMenu menu = new USSDMenu(getMessage(thisSection, createGroupMenu + doSuffix, promptKey + ".location",
                joiningCode, user));

        menu.addMenuOption(groupMenuWithId("public", groupUid) + "&useLocation=true",
                getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "public.location", user));
        menu.addMenuOption(groupMenuWithId("public", groupUid) + "&useLocation=false",
                getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "public.nolocation", user));
        menu.addMenuOption(groupMenuWithId("private", groupUid),
                getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "private", user));

        return menu;
    }

    @RequestMapping(value = groupPath + "public")
    public Request setGroupPublic(@RequestParam(phoneNumber) String inputNumber, @RequestParam(groupUidParam) String groupUid,
                                  @RequestParam boolean useLocation) throws  URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        Group group = groupBroker.load(groupUid);
        groupBroker.updateDiscoverable(user.getUid(), groupUid, true, inputNumber);
        if (useLocation) {
            geoLocationBroker.logUserUssdPermission(user.getUid(), groupUid, JpaEntityType.GROUP, false);
        }
        return menuBuilder(postCreateOptionsNoLocation(groupUid, group.getName(),
                group.getGroupTokenCode(), user));
    }

    @RequestMapping(value = groupPath + "private")
    public Request setGroupPrivate(@RequestParam(phoneNumber) String inputNumber, @RequestParam(groupUidParam) String groupUid)
        throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        Group group = groupBroker.load(groupUid);
        groupBroker.updateDiscoverable(user.getUid(), groupUid, false, null);
        return menuBuilder(postCreateOptionsNoLocation(groupUid, group.getName(),
                group.getGroupTokenCode(), user));
    }

    @RequestMapping(value = groupPath + closeGroupToken)
    @ResponseBody
    public Request closeGroupTokenDo(@RequestParam(phoneNumber) String inputNumber,
                                     @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(closeGroupToken, groupUid));
        Group group = groupBroker.load(groupUid);

        /*  the only case of coming here and the group has a code is after interruption or after 'add numbers' via create
            hence there is no need to check if the code expiry date has passed (by definition, the code is valid) */

        groupBroker.closeJoinToken(user.getUid(), group.getUid());

        USSDMenu menu = new USSDMenu(getMessage(thisSection, closeGroupToken, promptKey, user));

        menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, groupUid),
                getMessage(thisSection, closeGroupToken, optionsKey + "add", user));
        menu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                getMessage(thisSection, closeGroupToken, optionsKey + "home", user));
        menu.addMenuOption("exit", getMessage("exit.option", user));

        return menuBuilder(menu);
    }

    /*
    Generates a loop, where it keeps asking for additional numbers and adds them to group over and over, until the
    user enters "0", when wrap up, and ask for the group name. Note: service layer will check permissions before adding,
    and USSD gateway restriction will provide a layer against URL hacking
     */

    @RequestMapping(value = groupPath + createGroupAddNumbers)
    @ResponseBody
    public Request createGroupAddNumbersOpeningPrompt(@RequestParam(phoneNumber) String inputNumber,
                                                      @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddNumbers, groupUid));
        return menuBuilder(new USSDMenu(getMessage(thisSection, createGroupAddNumbers, promptKey, user),
                groupMenuWithId(createGroupAddNumbers + doSuffix, groupUid)));
    }

    @RequestMapping(value = groupPath + createGroupAddNumbers + doSuffix)
    @ResponseBody
    public Request addNumbersToNewlyCreatedGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                                 @RequestParam(value = groupUidParam, required = true) String groupUid,
                                                 @RequestParam(value = userInputParam, required = true) String userInput,
                                                 @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

        USSDMenu thisMenu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber,
                saveGroupMenuWithInput(createGroupAddNumbers + doSuffix, groupUid, userResponse, false));

        if (!"0".equals(userResponse.trim())) {
            thisMenu = ussdGroupUtil.addNumbersToExistingGroup(user, groupUid, thisSection,
                    userResponse, createGroupAddNumbers + doSuffix);
        } else { // stop asking for numbers, reset interrupt prompt and give options to go back
            Group group = groupBroker.load(groupUid);
            String prompt = (group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime())) ?
                    getMessage(thisSection, createGroupAddNumbers, promptKey + ".done.token", group.getGroupTokenCode(), user) :
                    getMessage(thisSection, createGroupAddNumbers, promptKey + ".done", user);
            thisMenu = new USSDMenu(prompt);
            thisMenu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
                    getMessage(thisSection, createGroupAddNumbers, optionsKey + "home", user));
            thisMenu.addMenuOption(groupMenus + closeGroupToken + groupUidUrlSuffix + groupUid,
                    getMessage(thisSection, createGroupAddNumbers, optionsKey + "token", user));
            thisMenu.addMenuOption("exit", getMessage("exit.option", user));
        }

        return menuBuilder(thisMenu);
    }


    @RequestMapping(value = groupPath + approveUser + doSuffix)
    @ResponseBody
    public Request approveUser(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        groupJoinRequestService.approve(user.getUid(), requestUid);
        final String prompt = getMessage(thisSection, approveUser, "approved", user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(groupMenus + startMenu, "Continue");

        return menuBuilder(menu);

    }

    @RequestMapping(value = groupPath + rejectUser + doSuffix)
    @ResponseBody
    public Request rejectUser(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        groupJoinRequestService.decline(user.getUid(), requestUid);
        final String prompt = getMessage(thisSection, approveUser, "rejected", user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(groupMenus + startMenu, getMessage(thisSection, approveUser, "continue", user));

        return menuBuilder(menu);
    }

    /**
     * SECTION: MENUS TO ADD MEMBERS, UNSUBSCRIBE, AND LIST MEMBERS
     */

    @RequestMapping(value = groupPath + addMemberPrompt)
    @ResponseBody
    public Request addNumberInput(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        Group group = groupBroker.load(groupUid);
        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(addMemberPrompt, groupUid));
        String promptMessage;

        if (group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime())) {
            promptMessage = getMessage(thisSection, addMemberPrompt, promptKey + ".token", group.getGroupTokenCode(), sessionUser);
        } else {
            promptMessage = getMessage(thisSection, addMemberPrompt, promptKey, sessionUser);
        }

        return menuBuilder(new USSDMenu(promptMessage, groupMenus + addMemberPrompt + doSuffix + groupUidUrlSuffix + groupUid));
    }

    private USSDMenu notifyGroupSizeLimitExceeded(Group group, User user) {
        USSDMenu menu = new USSDMenu(getMessage("group.add.limit.exceeded", new String[] { group.getName() }, user));
        menu.addMenuOption("back", "back");
        menu.addMenuOptions(optionsHomeExit(user, true));
        return menu;
    }

    @RequestMapping(value = groupPath + addMemberPrompt + doSuffix)
    @ResponseBody
    public Request addNumberToGroup(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = groupUidParam) String groupUid,
                                    @RequestParam(value = userInputParam) String numberToAdd) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        USSDMenu thisMenu = (numberToAdd.trim().equals("0")) ?
                new USSDMenu(getMessage(thisSection, addMemberPrompt + doSuffix, promptKey, sessionUser), optionsHomeExit(sessionUser, false)) :
                ussdGroupUtil.addNumbersToExistingGroup(sessionUser, groupUid, thisSection, numberToAdd, addMemberPrompt + doSuffix);

        return menuBuilder(thisMenu);
    }

    @RequestMapping(value = groupPath + unsubscribePrompt)
    @ResponseBody
    public Request unsubscribeConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(unsubscribePrompt, groupUid));

        String menuKey = groupKey + "." + unsubscribePrompt + ".";
        USSDMenu promptMenu = new USSDMenu(getMessage(menuKey + promptKey, sessionUser));
        promptMenu.addMenuOption(groupMenuWithId(unsubscribePrompt + doSuffix, groupUid),
                getMessage(menuKey + optionsKey + "confirm", sessionUser));
        promptMenu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
                getMessage(menuKey + optionsKey + "back", sessionUser));

        return menuBuilder(promptMenu);

    }

    @RequestMapping(value = groupPath + unsubscribePrompt + doSuffix)
    @ResponseBody
    public Request unsubscribeDo(@RequestParam(value = phoneNumber) String inputNumber,
                                 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User sessionUser = userManager.findByInputNumber(inputNumber, null);
        // if user isn't part of group, this won't do anything; and USSD gateway IP restrictions mean fake-removing
        // someone would have to somehow fake the network call to the gateway
        groupBroker.unsubscribeMember(sessionUser.getUid(), groupUid);
        String returnMessage = getMessage(thisSection, unsubscribePrompt + doSuffix, promptKey, sessionUser);
        return menuBuilder(new USSDMenu(returnMessage, optionsHomeExit(sessionUser, false)));
    }

    private boolean isValidGroupName(String groupName) {
        return groupName.length() > 1;
    }

}