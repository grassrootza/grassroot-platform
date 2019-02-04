package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.exception.LocationNotAvailableException;
import za.org.grassroot.integration.exception.LocationTrackingImpossibleException;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collections;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;


@Service
public class UssdSafetyGroupServiceImpl implements UssdSafetyGroupService {
	private final Logger logger = LoggerFactory.getLogger(UssdSafetyGroupServiceImpl.class);

	private static final USSDSection thisSection = USSDSection.SAFETY_GROUP_MANAGER;

	private final UssdSupport ussdSupport;
	private final SafetyEventBroker safetyEventBroker;
	private final GroupQueryBroker groupQueryBroker;
	private final UserManagementService userManager;
	private final AddressBroker addressBroker;
	private final GroupBroker groupBroker;
	private final UssdLocationServicesBroker locationServicesBroker;
	private final USSDGroupUtil groupUtil;
	private final CacheUtilService cacheManager;

	private final String safetyTriggerString;

	public UssdSafetyGroupServiceImpl(UssdSupport ussdSupport, CacheUtilService cacheManager, SafetyEventBroker safetyEventBroker, GroupQueryBroker groupQueryBroker, UserManagementService userManager, AddressBroker addressBroker, GroupBroker groupBroker, UssdLocationServicesBroker locationServicesBroker, USSDGroupUtil groupUtil,
									  @Value("${grassroot.ussd.joincode.format:*134*1994*%s#}") String ussdCodeFormat,
									  @Value("${grassroot.ussd.safety.code:911}") String safetyCode) {
		this.cacheManager = cacheManager;
		this.ussdSupport = ussdSupport;
		this.safetyEventBroker = safetyEventBroker;
		this.groupQueryBroker = groupQueryBroker;
		this.userManager = userManager;
		this.addressBroker = addressBroker;
		this.groupBroker = groupBroker;
		this.locationServicesBroker = locationServicesBroker;
		this.groupUtil = groupUtil;

		this.safetyTriggerString = String.format(ussdCodeFormat, safetyCode);
	}

	@Override
	public USSDMenu assemblePanicButtonActivationMenu(User user) {
		USSDMenu menu;
		if (user.hasSafetyGroup()) {
			boolean isBarred = safetyEventBroker.isUserBarred(user.getUid());
			String message = (!isBarred) ? ussdSupport.getMessage(USSDSection.HOME, "safety.activated", promptKey, user)
					: ussdSupport.getMessage(USSDSection.HOME, "safety.barred", promptKey, user);
			if (!isBarred) {
				safetyEventBroker.create(user.getUid(), user.getSafetyGroup().getUid());
			}
			menu = new USSDMenu(message);
		} else {
			menu = new USSDMenu(ussdSupport.getMessage(USSDSection.HOME, "safety.not-activated", promptKey, user));
			if (groupQueryBroker.fetchUserCreatedGroups(user, 0, 1).getTotalElements() != 0) {
				menu.addMenuOption(UssdSupport.safetyMenus + "pick-group", ussdSupport.getMessage(USSDSection.HOME, "safety", optionsKey + "existing", user));
			}
			menu.addMenuOption(UssdSupport.safetyMenus + "new-group", ussdSupport.getMessage(USSDSection.HOME, "safety", optionsKey + "new", user));
			menu.addMenuOption(startMenu, ussdSupport.getMessage(optionsKey + "back.main", user));
		}
		return menu;
	}

	@Override
	public USSDMenu assemblePanicButtonActivationResponse(User user, SafetyEvent safetyEvent) {
		String activateByDisplayName = safetyEvent.getActivatedBy().getDisplayName();
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(USSDSection.HOME, "safety.responder", promptKey, activateByDisplayName, user));
		menu.addMenuOptions(ussdSupport.optionsYesNo(user, USSDUrlUtil.safetyMenuWithId("record-response", safetyEvent.getUid())));
		return menu;
	}

	@Override
	@Transactional
	public Request processManageSafetyGroup(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		USSDMenu menu = user.hasSafetyGroup() ? createOpeningMenuHasGroup(user) : createOpeningMenuNoGroup(user);
		menu.addMenuOption(startMenu + "_force", ussdSupport.getMessage(optionsKey + "back.main", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processPickSafetyGroup(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		USSDMenu menu = groupUtil.showUserCreatedGroupsForSafetyFeature(user, thisSection,
				safetyMenus + pickGroup + doSuffix, 0);
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processPickSafetyGroupDo(String msisdn, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, USSDUrlUtil.saveSafetyGroupMenu(pickGroup + doSuffix, groupUid, null));
		safetyEventBroker.setSafetyGroup(user.getUid(), groupUid);
		cacheManager.clearUssdMenuForUser(user.getPhoneNumber());
		String prompt = ussdSupport.getMessage(thisSection, pickGroup, promptKey + ".done", new String[]{
				groupUtil.getGroupName(groupUid), safetyTriggerString}, user);
		USSDMenu menu = new USSDMenu(prompt, ussdSupport.optionsHomeExit(user, false));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRequestLocationTracking(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);

		final String prompt = ussdSupport.getMessage(thisSection, "tracking.request", promptKey, user);
		USSDMenu menu = new USSDMenu(prompt, ussdSupport.optionsYesNo(user,
				safetyMenus + "location/request/allowed?dummy=1", // use dummy else URL is malformed
				safetyMenus + "location/request/denied?dummy=1"));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processApproveLocationTracking(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);

		try {
			boolean lookupAdded = locationServicesBroker.addUssdLocationLookupAllowed(user.getUid(), UserInterfaceType.USSD);
			final String menuPrompt = ussdSupport.getMessage(thisSection, "tracking.request", lookupAdded ? "succeeded" : "failed", user);
			USSDMenu menu = new USSDMenu(menuPrompt, ussdSupport.optionsHomeExit(user, true));
			return ussdSupport.menuBuilder(menu);
		} catch (LocationTrackingImpossibleException e) {
			USSDMenu menu2 = new USSDMenu(ussdSupport.getMessage(thisSection, "tracking.request", "failed", user),
					ussdSupport.optionsHomeExit(user, false));
			return ussdSupport.menuBuilder(menu2);
		}
	}

	@Override
	@Transactional
	public Request processRevokeLocationTracking(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		try {
			boolean lookupRemoved = locationServicesBroker.removeUssdLocationLookup(user.getUid(), UserInterfaceType.USSD);
			final String menuPrompt = ussdSupport.getMessage(thisSection, "tracking.revoke", lookupRemoved ? "succeeded" : "failed", user);
			return ussdSupport.menuBuilder(new USSDMenu(menuPrompt, ussdSupport.optionsHomeExit(user, true)));
		} catch (LocationNotAvailableException e) {
			return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, "tracking.request", "nottracked", user),
					ussdSupport.optionsHomeExit(user, false)));
		}
	}

	@Override
	@Transactional
	public Request processCheckCurrentLocation(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		try {
			GeoLocation location = locationServicesBroker.getUssdLocationForUser(user.getUid());
			// todo: double check address is not null
			Address address = addressBroker.getAndStoreAddressFromLocation(user.getUid(), location, UserInterfaceType.USSD, false);
			final NumberFormat coordFormat = new DecimalFormat("#.##");
			final String prompt = ussdSupport.getMessage(thisSection, "tracking.current", promptKey, new String[]{
					coordFormat.format(location.getLatitude()),
					coordFormat.format(location.getLongitude()),
					getShortDescription(address)
			}, user);
			USSDMenu menu = new USSDMenu(prompt);
			menu.addMenuOption(locationUrl("current/confirm", address.getUid(), location),
					ussdSupport.getMessage("options.yes", user));
			menu.addMenuOption(locationUrl("current/change", address.getUid(), location),
					ussdSupport.getMessage("options.no", user));
			return ussdSupport.menuBuilder(menu);
		} catch (Exception e) {
			e.printStackTrace();
			final String errorP = ussdSupport.getMessage(thisSection, "tracking.current", "error", user);
			USSDMenu menu = new USSDMenu(errorP);
			menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage("options.back", user));
			menu.addMenuOption(startMenu, ussdSupport.getMessage("options.back.main", user));
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processRespondToCurrentLocation(String msisdn, String addressUid, double latitude, double longitude) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		GeoLocation location = new GeoLocation(latitude, longitude);
		addressBroker.confirmLocationAddress(user.getUid(), addressUid, location, UserInterfaceType.USSD);
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, "current.confirm", promptKey, user),
				ussdSupport.optionsHomeExit(user, true)));
	}

	@Override
	@Transactional
	public Request processChangeCurrentLocation(String msisdn, String addressUid, double latitude, double longitude) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		GeoLocation location = new GeoLocation(latitude, longitude);
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "current.change", promptKey, user));
		menu.setFreeText(true);
		menu.setNextURI(locationUrl("current/describe", addressUid, location));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processDescribeCurrentLocation(String msisdn, String addressUid, double latitude, double longitude, String request) throws URISyntaxException {
		// todo : validate input
		User user = userManager.findByInputNumber(msisdn);
		addressBroker.reviseLocationAddress(user.getUid(), addressUid, new GeoLocation(latitude, longitude),
				request, UserInterfaceType.USSD);
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, "current.change", "done", request, user),
				ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processCreateGroup(String inputNumber, String groupName, boolean interrupted, String interGroupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		USSDMenu menu;
		if (!interrupted && !USSDGroupUtil.isValidGroupName(groupName)) {
			menu = groupUtil.invalidGroupNamePrompt(user, groupName, thisSection, createGroupMenu);
		} else {
			String groupUid;
			if (!interrupted) {
				MembershipInfo membershipInfo = new MembershipInfo(user.getPhoneNumber(), GroupRole.ROLE_GROUP_ORGANIZER, user.getDisplayName());
				Group group = groupBroker.create(user.getUid(), groupName, null, Collections.singleton(membershipInfo), GroupPermissionTemplate.DEFAULT_GROUP, null, null, false, false, false);
				groupUid = group.getUid();
				safetyEventBroker.setSafetyGroup(user.getUid(), groupUid);
			} else {
				groupUid = interGroupUid;
			}

			cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveSafetyGroupMenu(createGroupMenu, groupUid, null));
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, createGroupMenu, promptKey + ".done", user));
			menu.setFreeText(true);
			menu.setNextURI(safetyMenus + addRespondents + doSuffix + "?groupUid=" + groupUid);
		}
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAddRespondersPrompt(String inputNumber, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveSafetyGroupMenu(addRespondents, groupUid, null));
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, addRespondents, promptKey, user),
				groupMenuWithId(thisSection, addRespondents + doSuffix, groupUid)));
	}

	@Override
	@Transactional
	public Request processAddRespondentsToGroup(String inputNumber, String groupUid, String userInput, String priorInput) throws URISyntaxException {
		USSDMenu menu;
		final String userResponse = (priorInput == null) ? userInput : priorInput;
		User user = userManager.findByInputNumber(inputNumber);

		if (!"0".equals(userResponse.trim())) {
			menu = groupUtil.addNumbersToExistingGroup(user, groupUid, thisSection, userResponse, addRespondents + doSuffix);
			cacheManager.putUssdMenuForUser(inputNumber, saveSafetyGroupMenu(addRespondents + doSuffix, groupUid, userResponse));
		} else {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, addRespondents, promptKey + ".done", user));
			if (!addressBroker.hasAddress(user.getUid())) {
				menu.addMenuOption(safetyMenus + addAddress, ussdSupport.getMessage(thisSection, addRespondents, optionsKey + "address", user));
			}
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
			cacheManager.clearUssdMenuForUser(inputNumber);
		}
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processResetPrompt(String inputNumber) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveSafetyMenuPrompt(resetSafetyGroup));
		Group group = user.getSafetyGroup();

		if (group == null) {
			throw new UnsupportedOperationException("Error! This menu should not be called on a user without a safety group");
		}

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, resetSafetyGroup, promptKey, user));
		if (group.getDescendantEvents().isEmpty() && group.getDescendantTodos().isEmpty()) { // todo : test descendant todo
			menu.addMenuOption(safetyMenus + resetSafetyGroup + doSuffix + "?deactivate=true",
					ussdSupport.getMessage(thisSection, resetSafetyGroup, optionsKey + "deactivate", user));
			menu.addMenuOption(safetyMenus + resetSafetyGroup + doSuffix, ussdSupport.getMessage(thisSection, resetSafetyGroup, optionsKey + "active", user));
		} else {
			menu.addMenuOption(safetyMenus + resetSafetyGroup + doSuffix, ussdSupport.getMessage(optionsKey + "yes", user));
		}
		menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(optionsKey + "back", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processResetDo(String inputNumber, boolean deactivate, boolean interrupted) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveSafetyMenuPrompt(resetSafetyGroup + doSuffix));
		if (!interrupted) {
			safetyEventBroker.resetSafetyGroup(user.getUid(), deactivate);
		}

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, resetSafetyGroup, promptKey + ".done", user));
		menu.addMenuOption(safetyMenus + newGroup, ussdSupport.getMessage(thisSection, resetSafetyGroup, optionsKey + "create", user));
		menu.addMenuOption(safetyMenus + pickGroup, ussdSupport.getMessage(thisSection, resetSafetyGroup, optionsKey + "pick", user));
		menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(thisSection, "group", optionsKey + "home", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processViewAddress(String msisdn) throws URISyntaxException {
		final User user = userManager.findByInputNumber(msisdn, saveSafetyMenuPrompt(viewAddress));
		final Address address = addressBroker.getUserAddress(user.getUid());
		final String[] fields = new String[]{address.getHouse(), address.getStreet(), address.getNeighbourhood()};
		final String prompt = StringUtils.isEmpty(address.getNeighbourhood()) ? ussdSupport.getMessage(thisSection, viewAddress, promptKey + ".notown", fields, user)
				: ussdSupport.getMessage(thisSection, viewAddress, promptKey, fields, user);
		USSDMenu menu = new USSDMenu(prompt);
		menu.addMenuOption(safetyMenus + changeAddress + doSuffix, ussdSupport.getMessage(thisSection, viewAddress, optionsKey + "change", user));
		menu.addMenuOption(safetyMenus + removeAddress, ussdSupport.getMessage(thisSection, viewAddress, optionsKey + "remove", user));
		menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(thisSection, "group", optionsKey + "home", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAddAddress(String msisdn, String fieldValue, boolean interrupted, String field) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, field == null ? saveSafetyMenuPrompt(addAddress) : saveAddressMenu(addAddress, field));
		// note: this will recursively call itself until done
		return ussdSupport.menuBuilder(getAddressMenu(field, user, fieldValue, interrupted));
	}

	@Override
	@Transactional
	public Request processChangeAddressPrompt(String msisdn, String field) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveAddressMenu(changeAddress, field));
		USSDMenu menu;
		if ("house".equals(field)) {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address", promptKey + ".house", user));
			menu.setFreeText(true);
			menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "house");
		} else if ("street".equals(field)) {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address", promptKey + ".street", user));
			menu.setFreeText(true);
			menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "street");
		} else {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address", promptKey + ".town", user));
			menu.setFreeText(true);
			menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "town");
		}
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processChangeAddressDo(String msisdn, String fieldValue, boolean interrupted, String field) throws URISyntaxException {
		final User user = userManager.findByInputNumber(msisdn, saveAddressMenu(changeAddress + doSuffix, field));

		if (field != null && !interrupted) {
			switch (field) {
				case "house":
					addressBroker.updateUserAddress(user.getUid(), fieldValue, null, null);
					break;
				case "street":
					addressBroker.updateUserAddress(user.getUid(), null, fieldValue, null);
					break;
				case "town":
					addressBroker.updateUserAddress(user.getUid(), null, null, fieldValue);
					break;
				default:
					throw new IllegalArgumentException("field cannot be null");
			}
		}

		Address address = addressBroker.getUserAddress(user.getUid());
		String[] confirmFields = new String[]{address.getHouse(), address.getStreet(), address.getNeighbourhood()};
		final String confirmPrompt = StringUtils.isEmpty(address.getNeighbourhood()) ? ussdSupport.getMessage(thisSection, "address.confirm", promptKey + ".notown", confirmFields, user)
				: ussdSupport.getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);

		final USSDMenu menu = new USSDMenu(confirmPrompt);
		menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "yes", user));
		menu.addMenuOption(safetyMenus + "change-address?field=house", ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "changehouse", user));
		menu.addMenuOption(safetyMenus + "change-address?field=street", ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "changestreet", user));
		menu.addMenuOption(safetyMenus + "change-address?field=town", ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "changearea", user));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRemoveAddress(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, USSDUrlUtil.saveSafetyMenuPrompt(removeAddress));
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address.remove", promptKey, user));
		menu.addMenuOption(safetyMenus + removeAddress + doSuffix, ussdSupport.getMessage(thisSection, "address.remove", optionsKey + "yes", user));
		menu.addMenuOption(safetyMenus + viewAddress, ussdSupport.getMessage(thisSection, "address.remove", optionsKey + "no", user));
		menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(optionsKey + "back", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRemoveAddressDo(String msisdn, boolean interrupted) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveSafetyMenuPrompt(removeAddress + doSuffix));
		if (!interrupted) {
			addressBroker.removeAddress(user.getUid());
		}
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, removeAddress, promptKey + ".done", user));
		menu.addMenuOption(safetyMenus + addAddress, ussdSupport.getMessage(thisSection, removeAddress, optionsKey + "new", user));
		menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(optionsKey + "back", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRecordResponse(String inputNumber, String safetyEventUid, boolean responded) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		final String prompt = (responded) ?
				ussdSupport.getMessage(thisSection, "response", promptKey + ".yes", user) :
				ussdSupport.getMessage(thisSection, "response", promptKey + ".no", user);
		final USSDMenu menu = new USSDMenu(prompt);
		if (responded) {
			cacheManager.clearSafetyEventResponseForUser(user, safetyEventBroker.load(safetyEventUid));
			menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=1",
					ussdSupport.getMessage(thisSection, "response", "option.valid", user));
			menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=0",
					ussdSupport.getMessage(thisSection, "response", "option.invalid", user));
		} else {
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
		}
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRecordValidity(String inputNumber, String safetyEventUid, boolean validity) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		//only one will be recorded, the cache will be cleared for others
		safetyEventBroker.recordResponse(user.getUid(), safetyEventUid, validity);
		String prompt = ussdSupport.getMessage(thisSection, "response", promptKey + ".thanks", user);
		USSDMenu menu = new USSDMenu(prompt);
		menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processNewGroup(String inputNumber) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveSafetyMenuPrompt(newGroup));
		return ussdSupport.menuBuilder(groupUtil.createGroupPrompt(user, thisSection, createGroupMenu));
	}

	private USSDMenu getAddressMenu(String field, User user, String fieldValue, boolean interrupted) {
		if (field == null) {
			logger.info("field passed as null, so starting at beginning ...");
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address", promptKey + ".house", user));
			menu.setFreeText(true);
			menu.setNextURI(thisSection.toPath() + "add-address?field=" + "house");
			return menu;

		} else if ("house".equals(field)) {
			if (!interrupted) {
				addressBroker.updateUserAddress(user.getUid(), fieldValue, null, null);
			}
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address", promptKey + ".street", user));
			menu.setFreeText(true);
			menu.setNextURI(thisSection.toPath() + "add-address?field=" + "street");
			return menu;

		} else if ("street".equals(field)) {
			if (!interrupted) {
				addressBroker.updateUserAddress(user.getUid(), null, fieldValue, null);
			}
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "address", promptKey + ".town", user));
			menu.setFreeText(true);
			menu.setNextURI(thisSection.toPath() + "add-address?field=" + "town");
			return menu;

		} else {
			if (!StringUtils.isEmpty(fieldValue) && !"0".equals(fieldValue.trim())) {
				addressBroker.updateUserAddress(user.getUid(), null, null, fieldValue);
			}

			Address address = addressBroker.getUserAddress(user.getUid());
			String confirmPrompt;
			String[] confirmFields;

			if (!StringUtils.isEmpty(address.getNeighbourhood())) {
				confirmFields = new String[]{address.getHouse(), address.getStreet(), address.getNeighbourhood()};
				confirmPrompt = ussdSupport.getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
			} else {
				confirmFields = new String[]{address.getHouse(), address.getStreet()};
				confirmPrompt = ussdSupport.getMessage(thisSection, "address.confirm.notown", promptKey, confirmFields, user);
			}

			USSDMenu menu = new USSDMenu(confirmPrompt);
			menu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "yes", user));
			menu.addMenuOption(safetyMenus + "change-address?&field=house", ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "changehouse", user));
			menu.addMenuOption(safetyMenus + "change-address?&field=street", ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "changestreet", user));
			menu.addMenuOption(safetyMenus + "change-address?&field=town", ussdSupport.getMessage(thisSection, "address.confirm", optionsKey + "changearea", user));
			return menu;
		}
	}

	private String locationUrl(String menu, String addressUid, GeoLocation location) {
		return safetyMenus + "location/" + menu + "?addressUid=" + addressUid +
				"&latitude=" + location.getLatitude() + "&longitude=" + location.getLongitude();
	}

	private String getShortDescription(Address address) {
		return address.getStreet() + ", " + address.getNeighbourhood();
	}

	private USSDMenu createOpeningMenuHasGroup(User user) {
		Group group = user.getSafetyGroup();

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, startMenu, promptKey + ".hasgroup", new String[]{group.getGroupName(),
				safetyTriggerString}, user));

		if (!locationServicesBroker.hasUserGivenLocationPermission(user.getUid())) {
			menu.addMenuOption(safetyMenus + "location/request",
					ussdSupport.getMessage(thisSection, startMenu, optionsKey + "track", user));
		} else {
			menu.addMenuOption(safetyMenus + "location/current",
					ussdSupport.getMessage(thisSection, startMenu, optionsKey + "location", user));
		}

		if (addressBroker.hasAddress(user.getUid())) {
			menu.addMenuOption(safetyMenus + viewAddress, ussdSupport.getMessage(thisSection, startMenu, optionsKey + viewAddress, user));
		} else {
			menu.addMenuOption(safetyMenus + addAddress, ussdSupport.getMessage(thisSection, startMenu, optionsKey + addAddress, user));
		}
		menu.addMenuOption(safetyMenus + addRespondents + "?groupUid=" + group.getUid(),
				ussdSupport.getMessage(thisSection, startMenu, optionsKey + "add.respondents", user));
		menu.addMenuOption(safetyMenus + resetSafetyGroup, ussdSupport.getMessage(thisSection, startMenu, optionsKey + resetSafetyGroup, user));
		return menu;
	}

	private USSDMenu createOpeningMenuNoGroup(User user) {
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, startMenu, promptKey + ".nogroup", user));
		if (groupQueryBroker.fetchUserCreatedGroups(user, 0, 1).getTotalElements() != 0) {
			menu.addMenuOption(safetyMenus + pickGroup, ussdSupport.getMessage(thisSection, startMenu, optionsKey + "existing", user));
		}
		menu.addMenuOption(safetyMenus + newGroup, ussdSupport.getMessage(thisSection, startMenu, optionsKey + "new", user));
		return menu;
	}

}
