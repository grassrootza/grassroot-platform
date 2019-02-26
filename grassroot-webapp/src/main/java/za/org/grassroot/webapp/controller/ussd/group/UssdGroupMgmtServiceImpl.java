package za.org.grassroot.webapp.controller.ussd.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.GroupRole;
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
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.ussd.UssdSupport;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.groupUidUrlSuffix;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

@Service
public class UssdGroupMgmtServiceImpl implements UssdGroupMgmtService {
	private final Logger log = LoggerFactory.getLogger(UssdGroupMgmtServiceImpl.class);

	private static final USSDSection thisSection = USSDSection.GROUP_MANAGER;

	private final GroupBroker groupBroker;
	private final GroupQueryBroker groupQueryBroker;
	private final PermissionBroker permissionBroker;
	private final UssdSupport ussdSupport;
	private final UserManagementService userManager;

	public UssdGroupMgmtServiceImpl(GroupBroker groupBroker, GroupQueryBroker groupQueryBroker, PermissionBroker permissionBroker, UssdSupport ussdSupport, UserManagementService userManager) {
		this.groupBroker = groupBroker;
		this.groupQueryBroker = groupQueryBroker;
		this.permissionBroker = permissionBroker;
		this.ussdSupport = ussdSupport;
		this.userManager = userManager;
	}

	@Override
	@Transactional
	public Request processRenamePrompt(String inputNumber, String groupUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu("rename", groupUid));
		Group groupToRename = groupBroker.load(groupUid);

		log.info("renaming group with this name : " + groupToRename.getGroupName());

		String promptMessage = (groupToRename.getGroupName().trim().length() == 0) ?
				ussdSupport.getMessage(thisSection, "rename", promptKey + "1", sessionUser) :
				ussdSupport.getMessage(thisSection, "rename", promptKey + "2", groupToRename.getGroupName(), sessionUser);

		return ussdSupport.menuBuilder(new USSDMenu(promptMessage, groupMenuWithId("rename" + doSuffix, groupUid)));
	}

	@Override
	@Transactional
	public Request processRenameGroup(String inputNumber, String groupUid, String newName, boolean interrupted) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		if (!interrupted) {
			groupBroker.updateName(user.getUid(), groupUid, newName);
		}

		USSDMenu thisMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "rename-do", promptKey, newName, user),
				ussdSupport.optionsHomeExit(user, false));

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processGroupVisibility(String inputNumber, String groupUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu("visibility", groupUid));
		Group group = groupBroker.load(groupUid);
		boolean isDiscoverable = group.isDiscoverable();

		log.info("changing group groupVisibility : " + group.getGroupName());

		String promptMessage = (isDiscoverable) ?
				ussdSupport.getMessage(thisSection, "visibility", promptKey + ".public", sessionUser)
				: ussdSupport.getMessage(thisSection, "visibility", promptKey + ".private", sessionUser);
		USSDMenu thisMenu = new USSDMenu(promptMessage);
		thisMenu.addMenuOption(groupVisibilityOption("visibility" + doSuffix, groupUid, isDiscoverable), "Yes");
		thisMenu.addMenuOption(groupMenuWithId("advanced", groupUid), "Back");

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processGroupVisibilityDo(String inputNumber, String groupUid, boolean isDiscoverable) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		groupBroker.updateDiscoverable(user.getUid(), groupUid, !isDiscoverable, inputNumber);
		String promptMessage = (isDiscoverable) ? ussdSupport.getMessage(thisSection, "visibility" + doSuffix, promptKey + ".private-done", user) :
				ussdSupport.getMessage(thisSection, "visibility" + doSuffix, promptKey + ".public-done", user);

		USSDMenu thisMenu = new USSDMenu(promptMessage);
		thisMenu.addMenuOption(groupMenuWithId("advanced", groupUid), "Back");

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processGroupToken(String inputNumber, String groupUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu("token", groupUid));
		Group sessionGroup = groupBroker.load(groupUid);

		USSDMenu tokenMenu;
		if (sessionGroup.hasValidGroupTokenCode()) {
			String tokenCode = sessionGroup.getGroupTokenCode();
			boolean indefiniteToken = sessionGroup.getTokenExpiryDateTime().equals(DateTimeUtil.getVeryLongAwayInstant());
			tokenMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "token", promptKey + ".exists", tokenCode, sessionUser));
			if (!indefiniteToken) {
				tokenMenu.addMenuOption(groupMenuWithId("token-extend", groupUid),
						ussdSupport.getMessage(thisSection, "token", optionsKey + "extend", sessionUser));
			}
			tokenMenu.addMenuOption(groupMenuWithId("token-close", groupUid),
					ussdSupport.getMessage(thisSection, "token", optionsKey + "close", sessionUser));

		} else {
			/* Creating a new token, ask for number of days, set an interruption flag */
			tokenMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "token", promptKey, sessionUser));
			String daysUrl = groupMenus + "token-do" + groupUidUrlSuffix + groupUid + "&days=";
			tokenMenu.addMenuOption(daysUrl + "0", ussdSupport.getMessage(thisSection, "token", "validity.permanent", sessionUser));
			tokenMenu.addMenuOption(daysUrl + "1", ussdSupport.getMessage(thisSection, "token", "validity.day", sessionUser));
			tokenMenu.addMenuOption(daysUrl + "7", ussdSupport.getMessage(thisSection, "token", "validity.week", sessionUser));
		}
		return ussdSupport.menuBuilder(tokenMenu);
	}

	@Override
	@Transactional
	public Request processCreateToken(String inputNumber, String groupUid, Integer daysValid) throws URISyntaxException {
		/* Generate a token, but also set the interruption switch back to null -- group creation is finished, if group was created */
		User user = userManager.findByInputNumber(inputNumber, null);
		LocalDateTime tokenExpiryDateTime = (daysValid == 0) ? null : LocalDateTime.now().plusDays(daysValid);
		String token = groupBroker.openJoinToken(user.getUid(), groupUid, tokenExpiryDateTime);
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, "token", "created", token, user),
				ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processExtendToken(String inputNumber, String groupUid, Integer daysValid) throws URISyntaxException {
		String urlToSave = (daysValid == null) ? saveGroupMenu("token-extend", groupUid) : null;
		User sessionUser = userManager.findByInputNumber(inputNumber, urlToSave);
		Group sessionGroup = groupBroker.load(groupUid);
		USSDMenu promptMenu;

		if (daysValid == null) {
			// means we are still asking for the number of days to extend
			promptMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "token", promptKey + ".extend", sessionUser));
			promptMenu.addMenuOption(groupMenus + "menu" + groupUidUrlSuffix + groupUid,
					ussdSupport.getMessage(thisSection, "token", optionsKey + "extend.none", sessionUser));
			String daySuffix = ussdSupport.getMessage(thisSection, "token", optionsKey + "days", sessionUser);
			for (int i = 1; i <= 3; i++)
				promptMenu.addMenuOption(groupMenuWithId("token-extend", groupUid) + "&days=" + i, i + daySuffix);
		} else {
			// we have been passed a number of days to extend
			LocalDateTime newExpiry = LocalDateTime.now().plusDays(daysValid);
			groupBroker.openJoinToken(sessionUser.getUid(), sessionGroup.getUid(), newExpiry);
			String date = newExpiry.format(dateFormat);
			promptMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "token", promptKey + ".extend.done", date, sessionUser),
					ussdSupport.optionsHomeExit(sessionUser, false));
		}
		return ussdSupport.menuBuilder(promptMenu);
	}

	@Override
	@Transactional
	public Request processCloseToken(String inputNumber, String groupUid, String confirmed) throws URISyntaxException {
		User user;
		USSDMenu thisMenu;
		if (confirmed == null) {
			user = userManager.findByInputNumber(inputNumber, saveGroupMenu("token" + "-close", groupUid));
			String beginUri = groupMenus + "token", endUri = groupUidUrlSuffix + groupUid;
			thisMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "token", promptKey + ".close", user), ussdSupport.optionsYesNo(user, beginUri + "-close" + endUri));
		} else if ("yes".equals(confirmed)) {
			user = userManager.findByInputNumber(inputNumber, null);
			groupBroker.closeJoinToken(user.getUid(), groupUid);
			thisMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "token", promptKey + ".close-done", user),
					ussdSupport.optionsHomeExit(user, false));
		} else {
			user = userManager.findByInputNumber(inputNumber, null);
			thisMenu = new USSDMenu("Okay, cancelled", ussdSupport.optionsHomeExit(user, false));
		}

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processPromptForAlias(String msisdn, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		Group group = groupBroker.load(groupUid);
		Membership membership = user.getMembership(group);
		String prompt = StringUtils.isEmpty(membership.getAlias()) ?
				ussdSupport.getMessage(thisSection, "alias", promptKey, user) :
				ussdSupport.getMessage(thisSection, "alias", promptKey + ".existing", membership.getAlias(), user);
		return ussdSupport.menuBuilder(new USSDMenu(prompt, groupMenuWithId("alias-do", groupUid)));
	}

	@Override
	@Transactional
	public Request processChangeAlias(String msisdn, String groupUid, String input) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		if (!StringUtils.isEmpty(input) && !"0".equals(input) && input.matches("[0-9]")) {
			String prompt = ussdSupport.getMessage(thisSection, "alias", promptKey + ".error", user);
			return ussdSupport.menuBuilder(new USSDMenu(prompt, groupMenuWithId("alias-do", groupUid)));
		} else {
			boolean resetName = StringUtils.isEmpty(input) || "0".equals(input);
			groupBroker.updateMemberAlias(user.getUid(), groupUid, resetName ? null : input);
			String renamed = resetName ? user.getDisplayName() : input;
			String prompt = ussdSupport.getMessage(thisSection, "alias", promptKey + ".done", renamed, user);
			return ussdSupport.menuBuilder(new USSDMenu(prompt, ussdSupport.optionsHomeExit(user, true)));
		}
	}

	@Override
	@Transactional
	public Request processInactiveConfirm(String inputNumber, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null); // since return flag may have been set prior
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "inactive", promptKey, user));
		menu.addMenuOption(groupMenuWithId("inactive" + doSuffix, groupUid),
				ussdSupport.getMessage(thisSection, "inactive", optionsKey + "confirm", user));
		menu.addMenuOption(groupMenuWithId("menu", groupUid),
				ussdSupport.getMessage(thisSection, "inactive", optionsKey + "cancel", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processInactiveDo(String inputNumber, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		USSDMenu menu;

		try {
			groupBroker.deactivate(user.getUid(), groupUid, true);
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "inactive" + doSuffix, promptKey + ".success", user), ussdSupport.optionsHomeExit(user, false));
		} catch (GroupDeactivationNotAvailableException e) {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "inactive" + doSuffix, errorPromptKey, user), ussdSupport.optionsHomeExit(user, false));
		}

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processListGroupMemberSize(String msisdn, String groupUid) throws URISyntaxException {
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

		final String prompt = ussdSupport.getMessage(thisSection, "list", promptKey, promptParams, user);

		USSDMenu menu = new USSDMenu(prompt);
		menu.addMenuOption(groupMenuWithId("menu", groupUid), ussdSupport.getMessage(thisSection, "list", optionsKey + "back", user));
		menu.addMenuOption(thisSection.toPath() + startMenu, ussdSupport.getMessage(thisSection, "list", optionsKey + "back-grp", user));
		menu.addMenuOption(startMenu, ussdSupport.getMessage(startMenu, user));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processsEndAllJoinCodesPrompt(String inputNumber) throws URISyntaxException {
		final User sessionUser = userManager.findByInputNumber(inputNumber);

		final String prompt = ussdSupport.getMessage(thisSection, "sent", "prompt", sessionUser);
		USSDMenu ussdMenu = new USSDMenu(prompt);

		log.debug("UserUid in USSDGroupController = {}", sessionUser.getUid());

		groupBroker.sendAllGroupJoinCodesNotification(sessionUser.getUid());

		ussdMenu.addMenuOption(thisSection.toPath() + startMenu,
				ussdSupport.getMessage(thisSection, "list", optionsKey + "back-grp", sessionUser));
		ussdMenu.addMenuOption("start_force", ussdSupport.getMessage("start", sessionUser));
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processsEndCreatedGroupJoinCode(String inputNumber, String groupUid) throws URISyntaxException {
		final User sessionUser = userManager.findByInputNumber(inputNumber);

		final String prompt = ussdSupport.getMessage(thisSection, "sent-code", "prompt", sessionUser);
		USSDMenu ussdMenu = new USSDMenu(prompt);

		//log.info("GroupUid in USSDGroupController = {}",group.getUid());

		groupBroker.sendGroupJoinCodeNotification(sessionUser.getUid(), groupUid);

		ussdMenu.addMenuOption(thisSection.toPath() + startMenu,
				ussdSupport.getMessage(thisSection, "list", optionsKey + "back-grp", sessionUser));
		ussdMenu.addMenuOption("start_force", ussdSupport.getMessage("start", sessionUser));

		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processSelectGroupLanguage(String inputNumber, String groupUid) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		final String prompt = ussdSupport.getMessage("group.language.prompt", user);
		USSDMenu menu = new USSDMenu(prompt, ussdSupport.languageOptions("group/language/select?groupUid=" + groupUid + "&language="));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processConfirmGroupLanguage(String inputNumber, String groupUid, String language) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		groupBroker.updateGroupDefaultLanguage(user.getUid(), groupUid, language, false);
		final String prompt = ussdSupport.getMessage("group.language.selected", user);
		USSDMenu menu = new USSDMenu(prompt, ussdSupport.optionsHomeExit(user, false));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAddGroupOrganizerPrompt(String inputNumber, String groupUid) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		final String prompt = ussdSupport.getMessage("group.organizer.add.prompt", user);
		final String url = groupMenuWithId("organizer/complete", groupUid);
		final USSDMenu menu = new USSDMenu(prompt, url);
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAddGroupOrganizerDo(String inputNumber, String groupUid, String organizerPhone) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		USSDMenu menu;
		if ("0".equals(organizerPhone.trim())) {
			// return the menu
			final String prompt = ussdSupport.getMessage("group.organizer.add.finished", user);
			menu = new USSDMenu(prompt);
			menu.addMenuOption(groupMenuWithId("menu", groupUid), ussdSupport.getMessage("options.back", user));
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, true));
		} else if (!PhoneNumberUtil.testInputNumber(inputNumber)) { // say it's wrong
			menu = new USSDMenu(ussdSupport.getMessage("group.organizer.add.error", user),
					groupMenuWithId("organizer/complete", groupUid));
		} else {
			MembershipInfo memberInfo = new MembershipInfo(organizerPhone, GroupRole.ROLE_GROUP_ORGANIZER, null);
			groupBroker.addMembers(user.getUid(), groupUid, Collections.singleton(memberInfo), GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
			menu = new USSDMenu(ussdSupport.getMessage("group.organizer.add.done", user),
					groupMenuWithId("organizer/complete", groupUid));
		}
		return ussdSupport.menuBuilder(menu);
	}
}
