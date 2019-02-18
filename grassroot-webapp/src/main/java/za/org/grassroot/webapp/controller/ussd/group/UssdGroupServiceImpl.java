package za.org.grassroot.webapp.controller.ussd.group;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.UssdSupport;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.groupUidUrlSuffix;
import static za.org.grassroot.webapp.enums.USSDSection.SAFETY_GROUP_MANAGER;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

@Service
public class UssdGroupServiceImpl implements UssdGroupService {

	private final boolean locationRequestEnabled;
	private final UssdSupport ussdSupport;
	private final GroupBroker groupBroker;
	private final PermissionBroker permissionBroker;
	private final GeoLocationBroker geoLocationBroker;
	private final GroupJoinRequestService groupJoinRequestService;
	private final USSDGroupUtil ussdGroupUtil;
	private final UserManagementService userManager;
	private final CacheUtilService cacheManager;

	public UssdGroupServiceImpl(@Value("${grassroot.ussd.location.enabled:false}") boolean locationRequestEnabled,
								UssdSupport ussdSupport, GroupBroker groupBroker, PermissionBroker permissionBroker, GeoLocationBroker geoLocationBroker, GroupJoinRequestService groupJoinRequestService, USSDGroupUtil ussdGroupUtil, UserManagementService userManager, CacheUtilService cacheManager) {
		this.locationRequestEnabled = locationRequestEnabled;
		this.ussdSupport = ussdSupport;
		this.groupBroker = groupBroker;
		this.permissionBroker = permissionBroker;
		this.geoLocationBroker = geoLocationBroker;
		this.groupJoinRequestService = groupJoinRequestService;
		this.ussdGroupUtil = ussdGroupUtil;
		this.userManager = userManager;
		this.cacheManager = cacheManager;
	}

	@Override
	@Transactional
	public Request processGroupPaginationHelper(String inputNumber, String prompt, Integer pageNumber, String existingUri, USSDSection section, String newUri) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		if (SAFETY_GROUP_MANAGER.equals(section)) {
			return ussdSupport.menuBuilder(ussdGroupUtil.showUserCreatedGroupsForSafetyFeature(user, SAFETY_GROUP_MANAGER, existingUri, pageNumber));
		} else {
			return ussdSupport.menuBuilder(ussdGroupUtil.userGroupMenuPaginated(user, prompt, existingUri, newUri, pageNumber, null, section));
		}
	}

	@Override
	@Transactional
	public Request processGroupList(String inputNumber, boolean interrupted) throws URISyntaxException {
		// in case went "back" from menu in middle of create group
		User user = (interrupted) ? userManager.findByInputNumber(inputNumber, null) : userManager.findByInputNumber(inputNumber);
		if (!groupJoinRequestService.getPendingRequestsForUser(user.getUid()).isEmpty()) {
			return ussdSupport.menuBuilder(ussdGroupUtil.showGroupJoinRequests(user, USSDSection.GROUP_MANAGER));
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
				return ussdSupport.menuBuilder(ussdGroupUtil.askForGroup(builder));
			} else {
				final String groupUid = permissionBroker.getActiveGroupsWithPermission(user, null).iterator().next().getUid();
				return ussdSupport.menuBuilder(ussdGroupUtil.existingGroupMenu(user, groupUid, true));
			}
		}
	}

	@Override
	@Transactional
	public Request processGroupMenu(String inputNumber, String groupUid) throws URISyntaxException {
		return (groupUid == null || groupUid.equals("")) ? processCreatePrompt(inputNumber) :
				ussdSupport.menuBuilder(ussdGroupUtil.existingGroupMenu(userManager.findByInputNumber(inputNumber), groupUid, false));
	}

	@Override
	@Transactional
	public Request processCreatePrompt(String inputNumber) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, groupMenus + createGroupMenu);
		return ussdSupport.menuBuilder(ussdGroupUtil.createGroupPrompt(sessionUser, thisSection, createGroupMenu + doSuffix));
	}

	@Override
	@Transactional
	public Request processCreateGroupWithName(String inputNumber, String groupName, boolean interrupted, String groupUid) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		Group createdGroup;
		USSDMenu menu;
		if (!isValidGroupName(groupName)) {
			cacheManager.putUssdMenuForUser(inputNumber, groupMenus + createGroupMenu);
			menu = ussdGroupUtil.invalidGroupNamePrompt(user, groupName, thisSection, createGroupMenu + doSuffix);
		} else {
			if (interrupted) {
				createdGroup = groupBroker.load(groupUid);
			} else {
				MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), GroupRole.ROLE_GROUP_ORGANIZER, user.getDisplayName());
				createdGroup = groupBroker.create(user.getUid(), groupName, null, Collections.singleton(creator),
						GroupPermissionTemplate.DEFAULT_GROUP, null, null, true, false, true);
			}

			cacheManager.putUssdMenuForUser(inputNumber, saveGroupMenuWithInput(createGroupMenu + doSuffix, createdGroup.getUid(), groupName, false));

			// UX feedback shows we should just remove the one after location. todo: use a dynamic config var to switch on/off
//			menu = !locationRequestEnabled ? postCreateOptionsNoLocation(createdGroup.getUid(), groupName, createdGroup.getGroupTokenCode(), user) :
//					postCreateOptionsWithLocation(createdGroup.getUid(), createdGroup.getGroupTokenCode(), user);
			menu = postCreateOptionsNoLocation(createdGroup.getUid(), groupName, createdGroup.getGroupTokenCode(), user);
		}
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processSetGroupPublic(String inputNumber, String groupUid, boolean useLocation) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Group group = groupBroker.load(groupUid);
		groupBroker.updateDiscoverable(user.getUid(), groupUid, true, inputNumber);
		if (useLocation) {
			geoLocationBroker.logUserUssdPermission(user.getUid(), groupUid, JpaEntityType.GROUP, false);
		}
		return ussdSupport.menuBuilder(postCreateOptionsNoLocation(groupUid, group.getName(), group.getGroupTokenCode(), user));
	}

	@Override
	@Transactional
	public Request processSetGroupPrivate(String inputNumber, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Group group = groupBroker.load(groupUid);
		groupBroker.updateDiscoverable(user.getUid(), groupUid, false, null);
		return ussdSupport.menuBuilder(postCreateOptionsNoLocation(groupUid, group.getName(), group.getGroupTokenCode(), user));
	}

	@Override
	@Transactional
	public Request processCloseGroupTokenDo(String inputNumber, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(closeGroupToken, groupUid));
		Group group = groupBroker.load(groupUid);

        /*  the only case of coming here and the group has a code is after interruption or after 'add numbers' via create
            hence there is no need to check if the code expiry date has passed (by definition, the code is valid) */

		groupBroker.closeJoinToken(user.getUid(), group.getUid());

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, closeGroupToken, promptKey, user));

		menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, groupUid),
				ussdSupport.getMessage(thisSection, closeGroupToken, optionsKey + "add", user));
		menu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
				ussdSupport.getMessage(thisSection, closeGroupToken, optionsKey + "home", user));
		menu.addMenuOption("exit", ussdSupport.getMessage("exit.option", user));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processCreateGroupAddNumbersOpeningPrompt(String inputNumber, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveGroupMenu(createGroupAddNumbers, groupUid));
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, createGroupAddNumbers, promptKey, user),
				groupMenuWithId(createGroupAddNumbers + doSuffix, groupUid)));
	}

	@Override
	@Transactional
	public Request processAddNumbersToNewlyCreatedGroup(String inputNumber, String groupUid, String userInput, String priorInput) throws URISyntaxException {
		final String userResponse = (priorInput == null) ? userInput : priorInput;
		User user = userManager.findByInputNumber(inputNumber,
				saveGroupMenuWithInput(createGroupAddNumbers + doSuffix, groupUid, userResponse, false));

		USSDMenu thisMenu;
		if (!"0".equals(userResponse.trim())) {
			thisMenu = ussdGroupUtil.addNumbersToExistingGroup(user, groupUid, thisSection,
					userResponse, createGroupAddNumbers + doSuffix);
		} else { // stop asking for numbers, reset interrupt prompt and give options to go back
			Group group = groupBroker.load(groupUid);
			String prompt = (group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime())) ?
					ussdSupport.getMessage(thisSection, createGroupAddNumbers, promptKey + ".done.token", group.getGroupTokenCode(), user) :
					ussdSupport.getMessage(thisSection, createGroupAddNumbers, promptKey + ".done", user);
			thisMenu = new USSDMenu(prompt);
			thisMenu.addMenuOption(groupMenus + startMenu + "?interrupted=1",
					ussdSupport.getMessage(thisSection, createGroupAddNumbers, optionsKey + "home", user));
			thisMenu.addMenuOption(groupMenus + closeGroupToken + groupUidUrlSuffix + groupUid,
					ussdSupport.getMessage(thisSection, createGroupAddNumbers, optionsKey + "token", user));
			thisMenu.addMenuOption("exit", ussdSupport.getMessage("exit.option", user));
		}

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processApproveUser(String inputNumber, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		groupJoinRequestService.approve(user.getUid(), requestUid);
		final String prompt = ussdSupport.getMessage(thisSection, approveUser, "approved", user);
		USSDMenu menu = new USSDMenu(prompt);
		menu.addMenuOption(groupMenus + startMenu, "Continue");

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRejectUser(String inputNumber, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		groupJoinRequestService.decline(user.getUid(), requestUid);
		final String prompt = ussdSupport.getMessage(thisSection, approveUser, "rejected", user);
		USSDMenu menu = new USSDMenu(prompt);
		menu.addMenuOption(groupMenus + startMenu, ussdSupport.getMessage(thisSection, approveUser, "continue", user));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAddNumberInput(String inputNumber, String groupUid) throws URISyntaxException {
		Group group = groupBroker.load(groupUid);
		User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(addMemberPrompt, groupUid));
		String promptMessage;

		if (group.getGroupTokenCode() != null && Instant.now().isBefore(group.getTokenExpiryDateTime())) {
			promptMessage = ussdSupport.getMessage(thisSection, addMemberPrompt, promptKey + ".token", group.getGroupTokenCode(), sessionUser);
		} else {
			promptMessage = ussdSupport.getMessage(thisSection, addMemberPrompt, promptKey, sessionUser);
		}

		return ussdSupport.menuBuilder(new USSDMenu(promptMessage, groupMenus + addMemberPrompt + doSuffix + groupUidUrlSuffix + groupUid));
	}

	@Override
	@Transactional
	public Request processAddNumberToGroup(String inputNumber, String groupUid, String numberToAdd) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, null);
		USSDMenu thisMenu = (numberToAdd.trim().equals("0")) ?
				new USSDMenu(ussdSupport.getMessage(thisSection, addMemberPrompt + doSuffix, promptKey, sessionUser), ussdSupport.optionsHomeExit(sessionUser, false)) :
				ussdGroupUtil.addNumbersToExistingGroup(sessionUser, groupUid, thisSection, numberToAdd, addMemberPrompt + doSuffix);

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processUnsubscribeConfirm(String inputNumber, String groupUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, saveGroupMenu(unsubscribePrompt, groupUid));

		String menuKey = groupKey + "." + unsubscribePrompt + ".";
		USSDMenu promptMenu = new USSDMenu(ussdSupport.getMessage(menuKey + promptKey, sessionUser));
		promptMenu.addMenuOption(groupMenuWithId(unsubscribePrompt + doSuffix, groupUid),
				ussdSupport.getMessage(menuKey + optionsKey + "confirm", sessionUser));
		promptMenu.addMenuOption(groupMenuWithId(existingGroupMenu, groupUid),
				ussdSupport.getMessage(menuKey + optionsKey + "back", sessionUser));

		return ussdSupport.menuBuilder(promptMenu);
	}

	@Override
	@Transactional
	public Request processUnsubscribeDo(String inputNumber, String groupUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, null);
		// if user isn't part of group, this won't do anything; and USSD gateway IP restrictions mean fake-removing
		// someone would have to somehow fake the network call to the gateway
		groupBroker.unsubscribeMember(sessionUser.getUid(), groupUid);
		String returnMessage = ussdSupport.getMessage(thisSection, unsubscribePrompt + doSuffix, promptKey, sessionUser);
		return ussdSupport.menuBuilder(new USSDMenu(returnMessage, ussdSupport.optionsHomeExit(sessionUser, false)));
	}

	@Override
	@Transactional(readOnly = true)
	public Request processAdvancedGroupMenu(String inputNumber, String groupUid) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		return ussdSupport.menuBuilder(ussdGroupUtil.advancedGroupOptionsMenu(user, groupUid));
	}

	private USSDMenu postCreateOptionsNoLocation(final String groupUid, final String groupName, final String joiningCode, User user) {
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, promptKey,
				new String[]{groupName, joiningCode}, user));

		menu.addMenuOption(groupMenuWithId(createGroupAddNumbers, groupUid),
				ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "numbers", user));
		menu.addMenuOption(groupMenuWithId(closeGroupToken, groupUid),
				ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "token", user));
		menu.addMenuOption(groupMenuWithId("sendall", groupUid),
				ussdSupport.getMessage(thisSection, "sendcode", promptKey, user));

		return menu;
	}

	private USSDMenu postCreateOptionsWithLocation(final String groupUid, final String joiningCode, User user) {
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, promptKey + ".location",
				joiningCode, user));

		menu.addMenuOption(groupMenuWithId("public", groupUid) + "&useLocation=true",
				ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "public.location", user));
		menu.addMenuOption(groupMenuWithId("public", groupUid) + "&useLocation=false",
				ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "public.nolocation", user));
		menu.addMenuOption(groupMenuWithId("private", groupUid),
				ussdSupport.getMessage(thisSection, createGroupMenu + doSuffix, optionsKey + "private", user));

		return menu;
	}

	private boolean isValidGroupName(String groupName) {
		return groupName.length() > 1;
	}

}
