package za.org.grassroot.webapp.controller.ussd.group;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.webapp.controller.ussd.USSDBaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

// holder for a bunch of relatively straightforward and self-contained group mgmt menus (e.g., renaming etc)
@Slf4j @RestController @RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDGroupMgmtController extends USSDBaseController {

    private static final String groupPath = homePath + groupMenus;
    private static final USSDSection thisSection = USSDSection.GROUP_MANAGER;

    private final GroupBroker groupBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final PermissionBroker permissionBroker;

    public USSDGroupMgmtController(GroupBroker groupBroker, GroupQueryBroker groupQueryBroker, PermissionBroker permissionBroker) {
        this.groupBroker = groupBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.permissionBroker = permissionBroker;
    }

    /*
    Menu options to rename a group, either existing, or if a new group, to give it a name
     */

    @RequestMapping(value = groupPath + "rename")
    @ResponseBody
    public Request renamePrompt(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu("rename", groupUid));
        Group groupToRename = groupBroker.load(groupUid);

        log.info("renaming group with this name : " + groupToRename.getGroupName());

        String promptMessage = (groupToRename.getGroupName().trim().length() == 0) ?
                getMessage(thisSection, "rename", promptKey + "1", sessionUser) :
                getMessage(thisSection, "rename", promptKey + "2", groupToRename.getGroupName(), sessionUser);

        return menuBuilder(new USSDMenu(promptMessage, groupMenuWithId("rename" + doSuffix, groupUid)));

    }

    @RequestMapping(value = groupPath + "rename-do")
    @ResponseBody
    public Request renameGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = userInputParam, required = true) String newName,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        if (!interrupted) groupBroker.updateName(user.getUid(), groupUid, newName);

        USSDMenu thisMenu = new USSDMenu(getMessage(thisSection, "rename-do", promptKey, newName, user),
                optionsHomeExit(user, false));

        return menuBuilder(thisMenu);
    }


    @RequestMapping(value = groupPath + "visibility")
    @ResponseBody
    public Request groupVisibility(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu("visibility", groupUid));
        Group group = groupBroker.load(groupUid);
        boolean isDiscoverable = group.isDiscoverable();

        log.info("changing group groupVisibility : " + group.getGroupName());

        String promptMessage = (isDiscoverable) ? getMessage(thisSection, "visibility", promptKey + ".public", sessionUser)
                : getMessage(thisSection, "visibility", promptKey + ".private", sessionUser);
        USSDMenu thisMenu = new USSDMenu(promptMessage);
        thisMenu.addMenuOption(groupVisibilityOption("visibility" + doSuffix, groupUid, isDiscoverable), "Yes");
        thisMenu.addMenuOption(groupMenuWithId("advanced", groupUid), "Back");

        return menuBuilder(thisMenu);

    }

    @RequestMapping(value = groupPath + "visibility" + doSuffix)
    @ResponseBody
    public Request groupVisibilityDo(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = groupUidParam) String groupUid,
                                     @RequestParam(value = "hide") boolean isDiscoverable) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        groupBroker.updateDiscoverable(user.getUid(), groupUid, !isDiscoverable, inputNumber);
        String promptMessage = (isDiscoverable) ? getMessage(thisSection, "visibility" + doSuffix, promptKey + ".private-done", user) :
                getMessage(thisSection, "visibility" + doSuffix, promptKey + ".public-done", user);

        USSDMenu thisMenu = new USSDMenu(promptMessage);
        thisMenu.addMenuOption(groupMenuWithId("advanced", groupUid), "Back");

        return menuBuilder(thisMenu);
    }

    /*
    SECTION: MENUS FOR GROUP TOKENS
     */

    @RequestMapping(value = groupPath + "token")
    @ResponseBody
    public Request groupToken(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        USSDMenu tokenMenu;

        User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu("token", groupUid));
        Group sessionGroup = groupBroker.load(groupUid);

        if (sessionGroup.hasValidGroupTokenCode()) {
            String tokenCode = sessionGroup.getGroupTokenCode();
            boolean indefiniteToken = sessionGroup.getTokenExpiryDateTime().equals(DateTimeUtil.getVeryLongAwayInstant());
            tokenMenu = new USSDMenu(getMessage(thisSection, "token", promptKey + ".exists", tokenCode, sessionUser));
            if (!indefiniteToken) tokenMenu.addMenuOption(groupMenuWithId("token-extend", groupUid),
                    getMessage(thisSection, "token", optionsKey + "extend", sessionUser));
            tokenMenu.addMenuOption(groupMenuWithId("token-close", groupUid),
                    getMessage(thisSection, "token", optionsKey + "close", sessionUser));

        } else {
            /* Creating a new token, ask for number of days, set an interruption flag */
            tokenMenu = new USSDMenu(getMessage(thisSection, "token", promptKey, sessionUser));
            String daysUrl = groupMenus + "token-do" + groupUidUrlSuffix + groupUid + "&days=";
            tokenMenu.addMenuOption(daysUrl + "0", getMessage(thisSection, "token", "validity.permanent", sessionUser));
            tokenMenu.addMenuOption(daysUrl + "1", getMessage(thisSection, "token", "validity.day", sessionUser));
            tokenMenu.addMenuOption(daysUrl + "7", getMessage(thisSection, "token", "validity.week", sessionUser));
        }
        return menuBuilder(tokenMenu);
    }

    @RequestMapping(value = groupPath + "token-do")
    @ResponseBody
    public Request createToken(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam) String groupUid,
                               @RequestParam(value = "days") Integer daysValid) throws URISyntaxException {

        /* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */
        User user = userManager.findByInputNumber(inputNumber, null);
        LocalDateTime tokenExpiryDateTime = (daysValid == 0) ? null : LocalDateTime.now().plusDays(daysValid);
        String token = groupBroker.openJoinToken(user.getUid(), groupUid, tokenExpiryDateTime);
        return menuBuilder(new USSDMenu(getMessage(thisSection, "token", "created", token, user),
                optionsHomeExit(user, false)));
    }

    @RequestMapping(value = groupPath + "token-extend")
    @ResponseBody
    public Request extendToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = groupUidParam, required = true) String groupUid,
                               @RequestParam(value = "days", required = false) Integer daysValid) throws URISyntaxException {

        String urlToSave = (daysValid == null) ? saveGroupMenu("token-extend", groupUid) : null;
        User sessionUser = userManager.findByInputNumber(inputNumber, urlToSave);
        Group sessionGroup = groupBroker.load(groupUid);
        USSDMenu promptMenu;

        if (daysValid == null) {
            // means we are still asking for the number of days to extend
            promptMenu = new USSDMenu(getMessage(thisSection, "token", promptKey + ".extend", sessionUser));
            promptMenu.addMenuOption(groupMenus + "menu" + groupUidUrlSuffix + groupUid,
                    getMessage(thisSection, "token", optionsKey + "extend.none", sessionUser));
            String daySuffix = getMessage(thisSection, "token", optionsKey + "days", sessionUser);
            for (int i = 1; i <= 3; i++)
                promptMenu.addMenuOption(groupMenuWithId("token-extend", groupUid) + "&days=" + i, i + daySuffix);
        } else {
            // we have been passed a number of days to extend
            LocalDateTime newExpiry = LocalDateTime.now().plusDays(daysValid);
            groupBroker.openJoinToken(sessionUser.getUid(), sessionGroup.getUid(), newExpiry);
            String date = newExpiry.format(dateFormat);
            promptMenu = new USSDMenu(getMessage(thisSection, "token", promptKey + ".extend.done", date, sessionUser),
                    optionsHomeExit(sessionUser, false));
        }
        return menuBuilder(promptMenu);
    }


    @RequestMapping(value = groupPath + "token-close")
    @ResponseBody
    public Request closeToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                              @RequestParam(value = groupUidParam, required = true) String groupUid,
                              @RequestParam(value = yesOrNoParam, required = false) String confirmed) throws URISyntaxException {

        User user;
        USSDMenu thisMenu;

        if (confirmed == null) {
            user = userManager.findByInputNumber(inputNumber, saveGroupMenu("token" + "-close", groupUid));
            String beginUri = groupMenus + "token", endUri = groupUidUrlSuffix + groupUid;
            thisMenu = new USSDMenu(getMessage(thisSection, "token", promptKey + ".close", user), optionsYesNo(user, beginUri + "-close" + endUri));
        } else if ("yes".equals(confirmed)) {
            user = userManager.findByInputNumber(inputNumber, null);
            groupBroker.closeJoinToken(user.getUid(), groupUid);
            thisMenu = new USSDMenu(getMessage(thisSection, "token", promptKey + ".close-done", user),
                    optionsHomeExit(user, false));
        } else {
            user = userManager.findByInputNumber(inputNumber, null);
            thisMenu = new USSDMenu("Okay, cancelled", optionsHomeExit(user, false));
        }

        return menuBuilder(thisMenu);
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
        String prompt = StringUtils.isEmpty(membership.getAlias()) ?
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
        if (!StringUtils.isEmpty(input) && !"0".equals(input) && input.matches("[0-9]")) {
            String prompt = getMessage(thisSection, "alias", promptKey + ".error", user);
            return menuBuilder(new USSDMenu(prompt, groupMenuWithId("alias-do", groupUid)));
        } else {
            boolean resetName = StringUtils.isEmpty(input) || "0".equals(input);
            groupBroker.updateMemberAlias(user.getUid(), groupUid, resetName ? null : input);
            String renamed = resetName ? user.getDisplayName() : input;
            String prompt = getMessage(thisSection, "alias", promptKey + ".done", renamed, user);
            return menuBuilder(new USSDMenu(prompt, optionsHomeExit(user, true)));
        }
    }

    /**
     * SECTION: GROUP INACTIVE AND LANGUAGE
     */

    @RequestMapping(value = groupPath + "inactive")
    @ResponseBody
    public Request inactiveConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null); // since return flag may have been set prior
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "inactive", promptKey, user));
        menu.addMenuOption(groupMenuWithId("inactive" + doSuffix, groupUid),
                getMessage(thisSection, "inactive", optionsKey + "confirm", user));
        menu.addMenuOption(groupMenuWithId("menu", groupUid),
                getMessage(thisSection, "inactive", optionsKey + "cancel", user));
        return menuBuilder(menu);

    }


    @RequestMapping(value = groupPath + "inactive" + doSuffix)
    @ResponseBody
    public Request inactiveDo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;

        try {
            groupBroker.deactivate(user.getUid(), groupUid, true);
            menu = new USSDMenu(getMessage(thisSection, "inactive" + doSuffix, promptKey + ".success", user), optionsHomeExit(user, false));
        } catch (GroupDeactivationNotAvailableException e) {
            menu = new USSDMenu(getMessage(thisSection, "inactive" + doSuffix, errorPromptKey, user), optionsHomeExit(user, false));
        }

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + "list")
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

        final String prompt = getMessage(thisSection, "list", promptKey, promptParams, user);

        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(groupMenuWithId("menu", groupUid), getMessage(thisSection, "list", optionsKey + "back", user));
        menu.addMenuOption(thisSection.toPath() + startMenu, getMessage(thisSection, "list", optionsKey + "back-grp", user));
        menu.addMenuOption(startMenu, getMessage(startMenu, user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + "sendall")
    @ResponseBody
    public Request sendAllJoinCodesPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        final User sessionUser = userManager.findByInputNumber(inputNumber);

        final String prompt = getMessage(thisSection,"sent","prompt",sessionUser);
        USSDMenu ussdMenu = new USSDMenu(prompt);

        log.debug("UserUid in USSDGroupController = {}",sessionUser.getUid());

        groupBroker.sendAllGroupJoinCodesNotification(sessionUser.getUid());

        ussdMenu.addMenuOption(thisSection.toPath() + startMenu,
                getMessage(thisSection, "list", optionsKey + "back-grp", sessionUser));
        ussdMenu.addMenuOption("start_force", getMessage("start", sessionUser));
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = groupPath + "send-code")
    @ResponseBody
    public  Request sendCreatedGroupJoinCode(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam String groupUid) throws URISyntaxException{
        final User sessionUser = userManager.findByInputNumber(inputNumber);

        final String prompt = getMessage(thisSection,"sent-code","prompt",sessionUser);
        USSDMenu ussdMenu = new USSDMenu(prompt);

        //log.info("GroupUid in USSDGroupController = {}",group.getUid());

        groupBroker.sendGroupJoinCodeNotification(sessionUser.getUid(), groupUid);

        ussdMenu.addMenuOption(thisSection.toPath() + startMenu,
                getMessage(thisSection, "list", optionsKey + "back-grp", sessionUser));
        ussdMenu.addMenuOption("start_force", getMessage("start", sessionUser));

        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = groupPath + "/language")
    @ResponseBody
    public Request selectGroupLanguage(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam String groupUid) throws URISyntaxException {
        final User user = userManager.findByInputNumber(inputNumber);
        final String prompt = getMessage("group.language.prompt", user);
        USSDMenu menu = new USSDMenu(prompt, languageOptions("group/language/select?groupUid=" + groupUid + "&language="));
        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + "/language/select")
    @ResponseBody
    public Request confirmGroupLanguage(@RequestParam(value = phoneNumber) String inputNumber,
                                        @RequestParam String groupUid,
                                        @RequestParam String language) throws URISyntaxException {
        final User user = userManager.findByInputNumber(inputNumber);
        groupBroker.updateGroupDefaultLanguage(user.getUid(), groupUid, language, false);
        final String prompt = getMessage("group.language.selected", user);
        USSDMenu menu = new USSDMenu(prompt, optionsHomeExit(user, false));
        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + "/organizer")
    @ResponseBody
    public Request addGroupOrganizerPrompt(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam String groupUid) throws URISyntaxException {
        final User user = userManager.findByInputNumber(inputNumber);
        final String prompt = getMessage("group.organizer.add.prompt", user);
        final String url = groupMenuWithId("organizer/complete", groupUid);
        final USSDMenu menu = new USSDMenu(prompt, url);
        return menuBuilder(menu);
    }

    @RequestMapping(value = groupPath + "/organizer/complete")
    @ResponseBody
    public Request addGroupOrganizerDo(@RequestParam(value = phoneNumber) String inputNumber,
                                       @RequestParam String groupUid,
                                       @RequestParam(value = userInputParam) String organizerPhone) throws URISyntaxException {
        final User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;
        if ("0".equals(organizerPhone.trim())) {
            // return the menu
            final String prompt = getMessage("group.organizer.add.finished", user);
            menu = new USSDMenu(prompt);
            menu.addMenuOption(groupMenuWithId("menu", groupUid), getMessage("options.back", user));
            menu.addMenuOptions(optionsHomeExit(user, true));
        } else if (!PhoneNumberUtil.testInputNumber(inputNumber)) { // say it's wrong
            menu = new USSDMenu(getMessage("group.organizer.add.error", user),
                    groupMenuWithId("organizer/complete", groupUid));
        } else {
            MembershipInfo memberInfo = new MembershipInfo(organizerPhone, BaseRoles.ROLE_GROUP_ORGANIZER, null);
            groupBroker.addMembers(user.getUid(), groupUid, Collections.singleton(memberInfo), GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
            menu = new USSDMenu(getMessage("group.organizer.add.done", user),
                    groupMenuWithId("organizer/complete", groupUid));
        }
        return menuBuilder(menu);
    }
}
