package za.org.grassroot.webapp.controller.ussd;

import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.h2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.enums.USSDSection.HOME;
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
    private final GroupQueryBroker groupQueryBroker;
    private final GeoLocationBroker geoLocationBroker;
    private final GroupJoinRequestService groupJoinRequestService;

    @Setter(AccessLevel.PACKAGE) private USSDGroupUtil ussdGroupUtil;

    private static final String
            existingGroupMenu = "menu",
            advancedGroupMenu = "advanced",
            createGroupMenu = "create",
            closeGroupToken = "create-token",
            createGroupAddNumbers = "add-numbers",
            listGroupMembers = "list",
            renameGroupPrompt = "rename",
            hideGroupPrompt = "visibility",
            approveUser = "approve",
            rejectUser = "reject",
            addMemberPrompt = "addnumber", // probably should rename this to prevent confusion w/ above
            unsubscribePrompt = "unsubscribe",
            groupTokenMenu = "token",
            groupVisibility = "visibility",
            mergeGroupMenu = "merge",
            inactiveMenu = "inactive",
            validity = "validity",
            invalidGroups = "clean",
            sendJoidCodeForCreatedGroup = "send-code",
            sendAllGroupsJoinCodes = "sendall";

    private static final String groupPath = homePath + groupMenus;
    private static final USSDSection thisSection = USSDSection.GROUP_MANAGER;

    private static final String groupUidParam = "groupUid";

    @Autowired
    public USSDGroupController(GroupBroker groupBroker, PermissionBroker permissionBroker, GroupQueryBroker groupQueryBroker, GeoLocationBroker geoLocationBroker, GroupJoinRequestService groupJoinRequestService, USSDGroupUtil ussdGroupUtil) {
        this.groupBroker = groupBroker;
        this.permissionBroker = permissionBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.geoLocationBroker = geoLocationBroker;
        this.groupJoinRequestService = groupJoinRequestService;
        this.ussdGroupUtil = ussdGroupUtil;
    }

    /*
    Join code menu
     */
    protected USSDMenu lookForJoinCode(User user, String trailingDigits) {
        Optional<Group> searchResult = groupQueryBroker.findGroupFromJoinCode(trailingDigits.trim());
        if (searchResult.isPresent()) {
            Group group = searchResult.get();
            log.info("adding user via join code ...");
            groupBroker.addMemberViaJoinCode(user.getUid(), group.getUid(), trailingDigits, UserInterfaceType.USSD);
            String prompt = (group.hasName()) ?
                    getMessage(HOME, startMenu, promptKey + ".group.token.named", group.getGroupName(), user) :
                    getMessage(HOME, startMenu, promptKey + ".group.token.unnamed", user);
            return welcomeMenu(prompt, user);
        } else {
            return null;
        }
    }

    /*
    Pagination helper
     */
    @RequestMapping(value = homePath + "group_page")
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
            return menuBuilder(ussdGroupUtil.showGroupRequests(user, USSDSection.GROUP_MANAGER));
        } else {
            final int numberGroups = permissionBroker.countActiveGroupsWithPermission(user, null);
            if (numberGroups != 1) {
                USSDGroupUtil.GroupMenuBuilder builder = new USSDGroupUtil.GroupMenuBuilder(user, thisSection);
                builder.urlForExistingGroup(existingGroupMenu)
                        .urlForCreateNewGroupPrompt(createGroupMenu)
                        .urlToCreateNewGroup(createGroupMenu + doSuffix)
                        .urlForNoGroups(createGroupMenu)
                        .urlForSendAllGroupJoinCodes(sendAllGroupsJoinCodes)
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
                        GroupPermissionTemplate.DEFAULT_GROUP, null, null, true, false);
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
        menu.addMenuOption(groupMenuWithId(sendJoidCodeForCreatedGroup,groupUid),
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

    /*
    Menu options to rename a group, either existing, or if a new group, to give it a name
     */

    @RequestMapping(value = groupPath + renameGroupPrompt)
    @ResponseBody
    public Request renamePrompt(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(renameGroupPrompt, groupUid));
        Group groupToRename = groupBroker.load(groupUid);

        log.info("renaming group with this name : " + groupToRename.getGroupName());

        String promptMessage = (groupToRename.getGroupName().trim().length() == 0) ?
                getMessage(thisSection, renameGroupPrompt, promptKey + "1", sessionUser) :
                getMessage(thisSection, renameGroupPrompt, promptKey + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, groupMenuWithId(renameGroupPrompt + doSuffix, groupUid)));

    }

    @RequestMapping(value = groupPath + renameGroupPrompt + doSuffix)
    @ResponseBody
    public Request renameGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = userInputParam, required = true) String newName,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        if (!interrupted) groupBroker.updateName(user.getUid(), groupUid, newName);

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, renameGroupPrompt + doSuffix, promptKey, newName, user),
                optionsHomeExit(user, false));

        return menuBuilder(thisMenu);
    }


    @RequestMapping(value = groupPath + hideGroupPrompt)
    @ResponseBody
    public Request groupVisibility(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(hideGroupPrompt, groupUid));
        Group group = groupBroker.load(groupUid);
        boolean isDiscoverable = group.isDiscoverable();

        log.info("changing group groupVisibility : " + group.getGroupName());

        String promptMessage = (isDiscoverable) ? getMessage(thisSection, groupVisibility, promptKey + ".public", sessionUser)
                : getMessage(thisSection, groupVisibility, promptKey + ".private", sessionUser);
        USSDMenu thisMenu = new USSDMenu(promptMessage);
        thisMenu.addMenuOption(groupVisibilityOption(groupVisibility + doSuffix, groupUid, isDiscoverable), "Yes");
        thisMenu.addMenuOption(groupMenuWithId(advancedGroupMenu, groupUid), "Back");

        return menuBuilder(thisMenu);

    }

    @RequestMapping(value = groupPath + hideGroupPrompt + doSuffix)
    @ResponseBody
    public Request groupVisibilityDo(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = groupUidParam) String groupUid,
                                     @RequestParam(value = "hide") boolean isDiscoverable) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        groupBroker.updateDiscoverable(user.getUid(), groupUid, !isDiscoverable, inputNumber);
        String promptMessage = (isDiscoverable) ? getMessage(thisSection, groupVisibility + doSuffix, promptKey + ".private-done", user) :
                getMessage(thisSection, groupVisibility + doSuffix, promptKey + ".public-done", user);

        USSDMenu thisMenu = new USSDMenu(promptMessage);
        thisMenu.addMenuOption(groupMenuWithId(advancedGroupMenu, groupUid), "Back");


        return menuBuilder(thisMenu);

    }

    /*
    SECTION: MENUS FOR GROUP TOKENS
     */

    @RequestMapping(value = groupPath + groupTokenMenu)
    @ResponseBody
    public Request groupToken(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        USSDMenu tokenMenu;

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu, groupUid));
        Group sessionGroup = groupBroker.load(groupUid);

        if (sessionGroup.hasValidGroupTokenCode()) {
            String tokenCode = sessionGroup.getGroupTokenCode();
            boolean indefiniteToken = sessionGroup.getTokenExpiryDateTime().equals(DateTimeUtil.getVeryLongAwayInstant());
            tokenMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".exists", tokenCode, sessionUser));
            if (!indefiniteToken) tokenMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-extend", groupUid),
                    getMessage(thisSection, groupTokenMenu, optionsKey + "extend", sessionUser));
            tokenMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-close", groupUid),
                    getMessage(thisSection, groupTokenMenu, optionsKey + "close", sessionUser));

        } else {
            /* Creating a new token, ask for number of days, set an interruption flag */
            tokenMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey, sessionUser));
            String daysUrl = groupMenus + groupTokenMenu + doSuffix + groupUidUrlSuffix + groupUid + "&days=";
            tokenMenu.addMenuOption(daysUrl + "0", getMessage(thisSection, groupTokenMenu, validity + ".permanent", sessionUser));
            tokenMenu.addMenuOption(daysUrl + "1", getMessage(thisSection, groupTokenMenu, validity + ".day", sessionUser));
            tokenMenu.addMenuOption(daysUrl + "7", getMessage(thisSection, groupTokenMenu, validity + ".week", sessionUser));
        }
        return menuBuilder(tokenMenu);
    }

    @RequestMapping(value = groupPath + groupTokenMenu + doSuffix)
    @ResponseBody
    public Request createToken(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam) String groupUid,
                               @RequestParam(value = "days") Integer daysValid) throws URISyntaxException {

        /* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */
        User user = userManager.findByInputNumber(inputNumber, null);
        LocalDateTime tokenExpiryDateTime = (daysValid == 0) ? null : LocalDateTime.now().plusDays(daysValid);
        String token = groupBroker.openJoinToken(user.getUid(), groupUid, tokenExpiryDateTime);
        return menuBuilder(new USSDMenu(getMessage(thisSection, groupTokenMenu, "created", token, user),
                optionsHomeExit(user, false)));
    }

    @RequestMapping(value = groupPath + groupTokenMenu + "-extend")
    @ResponseBody
    public Request extendToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = "days", required = false) Integer daysValid) throws URISyntaxException {

        String urlToSave = (daysValid == null) ? saveGroupMenu(groupTokenMenu + "-extend", groupUid) : null;
        User sessionUser = userManager.findByInputNumber(inputNumber, urlToSave);
        Group sessionGroup = groupBroker.load(groupUid);
        USSDMenu promptMenu;

        if (daysValid == null) {
            // means we are still asking for the number of days to extend
            promptMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".extend", sessionUser));
            promptMenu.addMenuOption(groupMenus + existingGroupMenu + groupUidUrlSuffix + groupUid,
                    getMessage(thisSection, groupTokenMenu, optionsKey + "extend.none", sessionUser));
            String daySuffix = getMessage(thisSection, groupTokenMenu, optionsKey + "days", sessionUser);
            for (int i = 1; i <= 3; i++)
                promptMenu.addMenuOption(groupMenuWithId(groupTokenMenu + "-extend", groupUid) + "&days=" + i, i + daySuffix);
        } else {
            // we have been passed a number of days to extend
            LocalDateTime newExpiry = LocalDateTime.now().plusDays(daysValid);
            groupBroker.openJoinToken(sessionUser.getUid(), sessionGroup.getUid(), newExpiry);
            String date = newExpiry.format(dateFormat);
            promptMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".extend.done", date, sessionUser),
                    optionsHomeExit(sessionUser, false));
        }
        return menuBuilder(promptMenu);
    }


    @RequestMapping(value = groupPath + groupTokenMenu + "-close")
    @ResponseBody
    public Request closeToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                              @RequestParam(value = groupUidParam, required = true) String groupUid,
                              @RequestParam(value = yesOrNoParam, required = false) String confirmed) throws URISyntaxException {

        User user;
        USSDMenu thisMenu;

        if (confirmed == null) {
            user = userManager.findByInputNumber(inputNumber, saveGroupMenu(groupTokenMenu + "-close", groupUid));
            String beginUri = groupMenus + groupTokenMenu, endUri = groupUidUrlSuffix + groupUid;
            thisMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".close", user), optionsYesNo(user, beginUri + "-close" + endUri));
        } else if ("yes".equals(confirmed)) {
            user = userManager.findByInputNumber(inputNumber, null);
            groupBroker.closeJoinToken(user.getUid(), groupUid);
            thisMenu = new USSDMenu(getMessage(thisSection, groupTokenMenu, promptKey + ".close-done", user),
                    optionsHomeExit(user, false));
        } else {
            user = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu("Okay, cancelled", optionsHomeExit(user, false));
        }

        return menuBuilder(thisMenu);

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

    /*
    SETTING ALIAS FOR GROUP
     */
    @RequestMapping(value = groupPath + "alias")
    @ResponseBody
    public Request promptForAlias(@RequestParam(value = phoneNumber) String msisdn, @RequestParam String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        Group group = groupBroker.load(groupUid);
        Membership membership = group.getMembership(user);
        String prompt = StringUtils.isNullOrEmpty(membership.getAlias()) ?
                getMessage(thisSection, "alias", promptKey, user) :
                getMessage(thisSection, "alias", promptKey + ".existing", membership.getAlias(), user);
        return menuBuilder(new USSDMenu(prompt, groupMenuWithId("alias-do", groupUid)));
    }

    @RequestMapping(value = groupPath + "alias-do")
    @ResponseBody
    public Request changeAlias(@RequestParam(value = phoneNumber) String msisdn,
                               @RequestParam String groupUid,
                               @RequestParam(value = userInputParam) String input) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (!StringUtils.isNullOrEmpty(input) && !"0".equals(input) && StringUtils.isNumber(input)) {
            String prompt = getMessage(thisSection, "alias", promptKey + ".error", user);
            return menuBuilder(new USSDMenu(prompt, groupMenuWithId("alias-do", groupUid)));
        } else {
            boolean resetName = StringUtils.isNullOrEmpty(input) || "0".equals(input);
            groupBroker.updateMemberAlias(user.getUid(), groupUid, resetName ? null : input);
            String renamed = resetName ? user.getDisplayName() : input;
            String prompt = getMessage(thisSection, "alias", promptKey + ".done", renamed, user);
            return menuBuilder(new USSDMenu(prompt, optionsHomeExit(user, true)));
        }
    }

    /**
     * SECTION: MERGING GROUP MENUS (AND DEACTIVATE)
     */

    @RequestMapping(value = groupPath + mergeGroupMenu)
    @ResponseBody
    public Request selectMergeGroups(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        USSDMenu menu;
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(mergeGroupMenu, groupUid));

        log.info("selecting merge candidates, with groupUid = {}", groupUid);
        Set<Group> mergeCandidates = groupQueryBroker.mergeCandidates(user.getUid(), groupUid);
        log.info("selected merge candidates, returned = {}", mergeCandidates);

        if (mergeCandidates.size() == 0) {
            menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu, promptKey + ".error", user));
            menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
                    getMessage(thisSection, mergeGroupMenu, optionsKey + "back", user));
            menu.addMenuOptions(optionsHomeExit(user, false));
        } else {
            menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu, promptKey, user));
            menu = ussdGroupUtil.addGroupsToMenu(menu, groupMenus + mergeGroupMenu + "-confirm?firstGroupSelected=" + groupUid,
                    new ArrayList<>(mergeCandidates), user);
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + "-confirm")
    @ResponseBody
    public Request confirmMerge(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = groupUidParam) String groupUid1,
                                @RequestParam(value = "firstGroupSelected") String firstGroupSelected) throws URISyntaxException {

        // todo: specify which one is smaller
        User user = userManager.findByInputNumber(inputNumber, saveGroupMenuWithParams(mergeGroupMenu + "-confirm", groupUid1,
                        "&firstGroupSelected=" + firstGroupSelected));
        String[] groupNames = new String[]{groupBroker.load(groupUid1).getName(""), groupBroker.load(firstGroupSelected).getName("")};

        USSDMenu menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu + "-confirm", promptKey, groupNames, user));
        String urlRoot = groupMenus + mergeGroupMenu + doSuffix + "&groupUid1=" + groupUid1 + "&groupUid2=" + firstGroupSelected + "&action=";
        String messageRoot = thisSection.toKey() + mergeGroupMenu + "-confirm." + optionsKey;

        menu.addMenuOption(urlRoot + "inactive", getMessage(messageRoot + "yes.inactive", user));
        menu.addMenuOption(urlRoot + "active", getMessage(messageRoot + "yes.active", user));
        menu.addMenuOption(urlRoot + "new", getMessage(messageRoot + "yes.newgroup", user));
        menu.addMenuOption(groupMenuWithId(mergeGroupMenu, firstGroupSelected), getMessage(messageRoot + "change.second", user));
        menu.addMenuOption(groupMenus + existingGroupMenu, getMessage(messageRoot + "change.both", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + "-newname")
    @ResponseBody
    public Request nameNewMergedGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                      @RequestParam(value = "groupUid1") String groupUid1,
                                      @RequestParam(value = "groupUid2") String groupUid2) throws URISyntaxException {

        String groupsSuffix = "?groupUid1=" + groupUid1 + "&groupUid2=" + groupUid2;
        User user = userManager.findByInputNumber(inputNumber, groupMenus + mergeGroupMenu + "-newname" + groupsSuffix);
        return menuBuilder(new USSDMenu(getMessage(thisSection, mergeGroupMenu + "-newname", promptKey, user),
                groupMenus + mergeGroupMenu + doSuffix + groupsSuffix));
    }

    @RequestMapping(value = groupPath + mergeGroupMenu + doSuffix)
    @ResponseBody
    public Request mergeDo(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                           @RequestParam(value = groupUidParam + "1", required = true) String firstGroupUid,
                           @RequestParam(value = groupUidParam + "2", required = true) String secondGroupUid,
                           @RequestParam(value = "action", required = false) String action,
                           @RequestParam(value = userInputParam, required = false) String userInput) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null); // resetting return flag
        Group firstGroup = groupBroker.load(firstGroupUid);
        Group secondGroup = groupBroker.load(secondGroupUid);

        Group resultGroup;

        switch (action) {
            case "inactive":
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), false, false, false, null);
                break;
            case "active":
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), true, false, false, null);
                break;
            case "new":
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), false, false, true, userInput);
                break;
            default:
                resultGroup = groupBroker.merge(user.getUid(), firstGroup.getUid(), secondGroup.getUid(), false, false, false, null);
                break;
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, mergeGroupMenu + doSuffix, promptKey, user));

        menu.addMenuOption(groupMenuWithId(existingGroupMenu, resultGroup.getUid()),
                getMessage(thisSection, mergeGroupMenu + doSuffix, optionsKey + "group", user));
        menu.addMenuOptions(optionsHomeExit(user, false));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + inactiveMenu)
    @ResponseBody
    public Request inactiveConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null); // since return flag may have been set prior
        USSDMenu menu = new USSDMenu(getMessage(thisSection, inactiveMenu, promptKey, user));
        menu.addMenuOption(groupMenuWithId(inactiveMenu + doSuffix, groupUid),
                getMessage(thisSection, inactiveMenu, optionsKey + "confirm", user));
        menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
                getMessage(thisSection, inactiveMenu, optionsKey + "cancel", user));
        return menuBuilder(menu);

    }


    @RequestMapping(value = groupPath + inactiveMenu + doSuffix)
    @ResponseBody
    public Request inactiveDo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;

        try {
            groupBroker.deactivate(user.getUid(), groupUid, true);
            menu = new USSDMenu(getMessage(thisSection, inactiveMenu + doSuffix, promptKey + ".success", user), optionsHomeExit(user, false));
        } catch (GroupDeactivationNotAvailableException e) {
            menu = new USSDMenu(getMessage(thisSection, inactiveMenu + doSuffix, errorPromptKey, user), optionsHomeExit(user, false));
        }

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + invalidGroups)
    @ResponseBody
    public Request listGroupsWithInvalidNames(@RequestParam String msisdn) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn);
	    Group group = groupQueryBroker.fetchGroupsWithOneCharNames(user, 2).get(0);

	    String createdDate = DateTimeUtil.convertToUserTimeZone(group.getCreatedDateTime(), DateTimeUtil.getSAST())
                .format(DateTimeFormatter.ofPattern("d MMM"));
	    String prompt = getMessage(thisSection, invalidGroups, promptKey, new String[] { group.getGroupName(), createdDate, String.valueOf(group.getMembers().size()) }, user);
	    USSDMenu menu = new USSDMenu(prompt);
	    menu.addMenuOption(groupMenuWithId(renameGroupPrompt, group.getUid()), getMessage(thisSection, invalidGroups, optionsKey + "rename", user));
	    menu.addMenuOption(groupMenuWithId(inactiveMenu + doSuffix, group.getUid()), getMessage(thisSection, invalidGroups, optionsKey + "delete", user));
	    menu.addMenuOption(groupMenus + startMenu, getMessage(thisSection, invalidGroups, optionsKey + "back", user));

	    return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + listGroupMembers)
    @ResponseBody
    public Request listGroupMemberSize(@RequestParam String msisdn, @RequestParam String groupUid) throws URISyntaxException {
        final User user = userManager.findByInputNumber(msisdn);
        final Group group = groupBroker.load(groupUid);

        // need to do this here as aren't calling service broker method ...
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        final GroupLog lastLog = groupQueryBroker.getMostRecentLog(group);
        final String lastModified = DateTimeUtil.convertToUserTimeZone(lastLog.getCreatedDateTime(), DateTimeUtil.getSAST())
                .format(DateTimeFormatter.ofPattern("dd-MM"));
        final String lastMessage = lastLog.getGroupLogType().toString();

        final int groupSize = group.getMemberships().size();
        final String[] promptParams = new String[]{String.valueOf(groupSize), lastModified, lastMessage};

        final String prompt = getMessage(thisSection, listGroupMembers, promptKey, promptParams, user);

        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid), getMessage(thisSection, listGroupMembers, optionsKey + "back", user));
        menu.addMenuOption(thisSection.toPath() + startMenu, getMessage(thisSection, listGroupMembers, optionsKey + "back-grp", user));
        menu.addMenuOption(startMenu, getMessage(startMenu, user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + sendAllGroupsJoinCodes)
    @ResponseBody
    public Request sendAllJoinCodesPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        final User sessionUser = userManager.findByInputNumber(inputNumber);

        final String prompt = getMessage(thisSection,"sent","prompt",sessionUser);
        USSDMenu ussdMenu = new USSDMenu(prompt);

        log.debug("UserUid in USSDGroupController = {}",sessionUser.getUid());

        groupBroker.sendAllGroupJoinCodesNotification(sessionUser.getUid());

        ussdMenu.addMenuOption(thisSection.toPath() + startMenu,
                getMessage(thisSection, listGroupMembers, optionsKey + "back-grp", sessionUser));
        ussdMenu.addMenuOption("start_force", getMessage("start", sessionUser));
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = groupPath + sendJoidCodeForCreatedGroup)
    @ResponseBody
    public  Request sendCreatedGroupJoinCode(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam String groupUid) throws URISyntaxException{
        final User sessionUser = userManager.findByInputNumber(inputNumber);

        final String prompt = getMessage(thisSection,"sent-code","prompt",sessionUser);
        USSDMenu ussdMenu = new USSDMenu(prompt);

        //log.info("GroupUid in USSDGroupController = {}",group.getUid());

        groupBroker.sendGroupJoinCodeNotification(sessionUser.getUid(), groupUid);

        ussdMenu.addMenuOption(thisSection.toPath() + startMenu,
                getMessage(thisSection, listGroupMembers, optionsKey + "back-grp", sessionUser));
        ussdMenu.addMenuOption("start_force", getMessage("start", sessionUser));

        return menuBuilder(ussdMenu);
    }

    private boolean isValidGroupName(String groupName) {
        return groupName.length() > 1;
    }

}