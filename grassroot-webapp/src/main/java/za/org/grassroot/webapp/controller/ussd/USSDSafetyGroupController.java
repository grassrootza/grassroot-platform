package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.exception.LocationNotAvailableException;
import za.org.grassroot.integration.exception.LocationTrackingImpossibleException;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.user.AddressBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import javax.annotation.PostConstruct;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Set;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by paballo on 2016/07/13.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDSafetyGroupController extends USSDBaseController {

    private static final Logger log = LoggerFactory.getLogger(USSDSafetyGroupController.class);

    private String safetyTriggerString;
    @Value("${grassroot.ussd.joincode.format:*134*1994*%s#}")
    private String ussdCodeFormat;
    @Value("${grassroot.ussd.safety.code:911}")
    private String safetyCode;

    private final AddressBroker addressBroker;
    private final GroupBroker groupBroker;
    private final GroupQueryBroker groupQueryBroker;
    private final SafetyEventBroker safetyEventBroker;

    private UssdLocationServicesBroker locationServicesBroker;
    private USSDGroupUtil groupUtil;

    private static final String
            createGroupMenu = "create",
            addRespondents = "add-numbers",
            safetyGroup = "safety",
            addAddress = "add-address",
            viewAddress = "view-address",
            removeAddress = "remove-address",
            changeAddress = "change-address",
            resetSafetyGroup = "reset",
            recordResponse = "record-response",
            recordValidity = "record-validity",
            pickGroup = "pick-group",
            newGroup ="new-group";

    private static final String safetyGroupPath = homePath + safetyGroup + "/";
    private static final USSDSection thisSection = USSDSection.SAFETY_GROUP_MANAGER;
    private static final String groupUidParam = "groupUid";

    @Autowired
    public USSDSafetyGroupController(AddressBroker addressBroker, GroupBroker groupBroker, GroupQueryBroker groupQueryBroker, SafetyEventBroker safetyEventBroker) {
        this.addressBroker = addressBroker;
        this.groupBroker = groupBroker;
        this.groupQueryBroker = groupQueryBroker;
        this.safetyEventBroker = safetyEventBroker;
    }

    @Autowired
    public void setGroupUtil(USSDGroupUtil groupUtil) {
        this.groupUtil = groupUtil;
    }

    @Autowired(required = false)
    public void setLocationServicesBroker(UssdLocationServicesBroker locationServicesBroker) {
        this.locationServicesBroker = locationServicesBroker;
    }

    @Autowired(required = false)
    public void setObjectLocationBroker(ObjectLocationBroker objectLocationBroker) {
    }

    @PostConstruct
    private void init() {
        safetyTriggerString = String.format(ussdCodeFormat, safetyCode);
    }

    protected USSDMenu assemblePanicButtonActivationMenu(User user) {
        USSDMenu menu;
        if (user.hasSafetyGroup()) {
            boolean isBarred = safetyEventBroker.isUserBarred(user.getUid());
            String message = (!isBarred) ? getMessage(USSDSection.HOME, "safety.activated", promptKey, user)
                    : getMessage(USSDSection.HOME, "safety.barred", promptKey, user);
            if (!isBarred) safetyEventBroker.create(user.getUid(), user.getSafetyGroup().getUid());
            menu = new USSDMenu(message);
        } else {
            menu = new USSDMenu(getMessage(USSDSection.HOME, "safety.not-activated", promptKey, user));
            if (groupQueryBroker.fetchUserCreatedGroups(user, 0, 1).getTotalElements() != 0) {
                menu.addMenuOption(safetyMenus + "pick-group", getMessage(USSDSection.HOME, "safety", optionsKey + "existing", user));
            }
            menu.addMenuOption(safetyMenus + "new-group", getMessage(USSDSection.HOME, "safety", optionsKey + "new", user));
            menu.addMenuOption(startMenu, getMessage(optionsKey + "back.main", user));
        }
        return menu;
    }

    protected USSDMenu assemblePanicButtonActivationResponse(User user, SafetyEvent safetyEvent) {
        String activateByDisplayName = safetyEvent.getActivatedBy().getDisplayName();
        USSDMenu menu = new USSDMenu(getMessage(USSDSection.HOME, "safety.responder", promptKey, activateByDisplayName, user));
        menu.addMenuOptions(optionsYesNo(user, USSDUrlUtil.safetyMenuWithId("record-response", safetyEvent.getUid())));
        return menu;
    }

    @RequestMapping(value = safetyGroupPath + startMenu)
    @ResponseBody
    public Request manageSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = user.hasSafetyGroup() ? createOpeningMenuHasGroup(user) : createOpeningMenuNoGroup(user);
        menu.addMenuOption(startMenu + "_force", getMessage(optionsKey + "back.main", user));
        return menuBuilder(menu);
    }

    private USSDMenu createOpeningMenuHasGroup(User user) {
        Group group = user.getSafetyGroup();

        USSDMenu menu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + ".hasgroup", new String[] { group.getGroupName(),
                safetyTriggerString }, user));

        if (!locationServicesBroker.hasUserGivenLocationPermission(user.getUid())) {
            menu.addMenuOption(safetyMenus + "location/request",
                    getMessage(thisSection, startMenu, optionsKey + "track", user));
        } else {
            menu.addMenuOption(safetyMenus + "location/current",
                    getMessage(thisSection, startMenu, optionsKey + "location", user));
        }

        if (addressBroker.hasAddress(user.getUid())) {
            menu.addMenuOption(safetyMenus+ viewAddress, getMessage(thisSection, startMenu, optionsKey + viewAddress, user));
        } else {
            menu.addMenuOption(safetyMenus + addAddress, getMessage(thisSection, startMenu, optionsKey + addAddress, user));
        }
        menu.addMenuOption(safetyMenus + addRespondents + "?groupUid=" + group.getUid(),
                getMessage(thisSection, startMenu, optionsKey + "add.respondents", user));
        menu.addMenuOption(safetyMenus + resetSafetyGroup, getMessage(thisSection, startMenu, optionsKey + resetSafetyGroup, user));
        return menu;
    }

    private USSDMenu createOpeningMenuNoGroup(User user) {
        USSDMenu menu = new USSDMenu(getMessage(thisSection, startMenu, promptKey + ".nogroup", user));
        if (groupQueryBroker.fetchUserCreatedGroups(user, 0, 1).getTotalElements() != 0) {
            menu.addMenuOption(safetyMenus + pickGroup, getMessage(thisSection, startMenu, optionsKey + "existing", user));
        }
        menu.addMenuOption(safetyMenus + newGroup, getMessage(thisSection, startMenu, optionsKey + "new", user));
        return menu;
    }

    @RequestMapping(value = safetyGroupPath + pickGroup)
    @ResponseBody
    public Request pickSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = groupUtil.showUserCreatedGroupsForSafetyFeature(user, thisSection,
                safetyMenus + pickGroup + doSuffix, 0);
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + pickGroup + doSuffix)
    @ResponseBody
    public Request pickSafetyGroupDo(@RequestParam String msisdn, @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, USSDUrlUtil.saveSafetyGroupMenu(pickGroup + doSuffix, groupUid, null));
        safetyEventBroker.setSafetyGroup(user.getUid(), groupUid);
        cacheManager.clearUssdMenuForUser(user.getPhoneNumber());
        String prompt = getMessage(thisSection, pickGroup, promptKey + ".done", new String[] {
                groupUtil.getGroupName(groupUid), safetyTriggerString }, user);
        USSDMenu menu = new USSDMenu(prompt, optionsHomeExit(user, false));
        return menuBuilder(menu);
    }

    /*
    SECTION: Request and grant permission to track location
     */

    @RequestMapping(value = safetyGroupPath + "location/request")
    @ResponseBody
    public Request requestLocationTracking(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);

        final String prompt = getMessage(thisSection, "tracking.request", promptKey, user);
        USSDMenu menu = new USSDMenu(prompt, optionsYesNo(user,
                safetyMenus + "location/request/allowed?dummy=1", // use dummy else URL is malformed
                safetyMenus + "location/request/denied?dummy=1"));

        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + "location/request/allowed")
    @ResponseBody
    public Request approveLocationTracking(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);

        try {
            boolean lookupAdded = locationServicesBroker.addUssdLocationLookupAllowed(user.getUid(), UserInterfaceType.USSD);
            final String menuPrompt = getMessage(thisSection, "tracking.request", lookupAdded ? "succeeded" : "failed", user);
            USSDMenu menu = new USSDMenu(menuPrompt, optionsHomeExit(user, true));
            return menuBuilder(menu);
        } catch (LocationTrackingImpossibleException e) {
            USSDMenu menu2 = new USSDMenu(getMessage(thisSection, "tracking.request", "failed", user),
                    optionsHomeExit(user, false));
            return menuBuilder(menu2);
        }
    }

    @RequestMapping(value = safetyGroupPath + "location/revoke")
    @ResponseBody
    public Request revokeLocationTracking(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        try {
            boolean lookupRemoved = locationServicesBroker.removeUssdLocationLookup(user.getUid(), UserInterfaceType.USSD);
            final String menuPrompt = getMessage(thisSection, "tracking.revoke", lookupRemoved ? "succeeded" : "failed", user);
            return menuBuilder(new USSDMenu(menuPrompt, optionsHomeExit(user, true)));
        } catch (LocationNotAvailableException e) {
            return menuBuilder(new USSDMenu(getMessage(thisSection, "tracking.request", "nottracked", user),
                    optionsHomeExit(user, false)));
        }
    }

    @RequestMapping(value = safetyGroupPath + "location/current")
    public Request checkCurrentLocation(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        try {
            GeoLocation location = locationServicesBroker.getUssdLocationForUser(user.getUid());
            // todo: double check address is not null
            Address address = addressBroker.getAndStoreAddressFromLocation(user.getUid(), location, UserInterfaceType.USSD, false);
            final NumberFormat coordFormat = new DecimalFormat("#.##");
            final String prompt = getMessage(thisSection, "tracking.current", promptKey, new String[] {
                    coordFormat.format(location.getLatitude()),
                    coordFormat.format(location.getLongitude()),
                    getShortDescription(address)
            }, user);
            USSDMenu menu = new USSDMenu(prompt);
            menu.addMenuOption(locationUrl("current/confirm", address.getUid(), location),
                    getMessage("options.yes", user));
            menu.addMenuOption(locationUrl("current/change", address.getUid(), location),
                    getMessage("options.no", user));
            return menuBuilder(menu);
        } catch (Exception e) {
            e.printStackTrace();
            final String errorP = getMessage(thisSection, "tracking.current", "error", user);
            USSDMenu menu = new USSDMenu(errorP);
            menu.addMenuOption(safetyMenus + startMenu, getMessage("options.back", user));
            menu.addMenuOption(startMenu, getMessage("options.back.main", user));
            return menuBuilder(menu);
        }
    }

    private String locationUrl(String menu, String addressUid, GeoLocation location) {
        return safetyMenus + "location/" + menu + "?addressUid=" + addressUid +
                "&latitude=" + location.getLatitude() + "&longitude=" + location.getLongitude();
    }

    @RequestMapping(value = safetyGroupPath + "location/current/confirm")
    public Request respondToCurrentLocation(@RequestParam String msisdn, @RequestParam String addressUid,
                                            @RequestParam double latitude, @RequestParam double longitude) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        GeoLocation location = new GeoLocation(latitude, longitude);
        addressBroker.confirmLocationAddress(user.getUid(), addressUid, location, UserInterfaceType.USSD);
        return menuBuilder(new USSDMenu(getMessage(thisSection, "current.confirm", promptKey, user),
                optionsHomeExit(user, true)));
    }

    @RequestMapping(value = safetyGroupPath + "location/current/change")
    public Request changeCurrentLocation(@RequestParam String msisdn, @RequestParam String addressUid,
                                         @RequestParam double latitude, @RequestParam double longitude) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        GeoLocation location = new GeoLocation(latitude, longitude);
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "current.change", promptKey, user));
        menu.setFreeText(true);
        menu.setNextURI(locationUrl("current/describe", addressUid, location));
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + "location/current/describe")
    public Request describeCurrentLocation(@RequestParam String msisdn, @RequestParam String addressUid,
                                           @RequestParam double latitude, @RequestParam double longitude,
                                           @RequestParam String request) throws URISyntaxException {
        // todo : validate input
        User user = userManager.findByInputNumber(msisdn);
        addressBroker.reviseLocationAddress(user.getUid(), addressUid, new GeoLocation(latitude, longitude),
                request, UserInterfaceType.USSD);
        return menuBuilder(new USSDMenu(getMessage(thisSection, "current.change", "done", request, user),
                optionsHomeExit(user, false)));
    }

    private String getShortDescription(Address address) {
        return address.getStreet() + ", " + address.getNeighbourhood();
    }

    /* @RequestMapping(value = safetyGroupPath + "location/current/response")
    public Request respondToCurrentLocation(@RequestParam String msisdn,
                                            @RequestParam String addressUid) {

    }*/

    /*
    SECTION: Creating a safety group
     */

    @RequestMapping(value = safetyGroupPath + newGroup)
    @ResponseBody
    public Request newGroup(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveSafetyMenuPrompt(newGroup));
        return menuBuilder(groupUtil.createGroupPrompt(user, thisSection, createGroupMenu));
    }

    @RequestMapping(value = safetyGroupPath + createGroupMenu)
    @ResponseBody
    public Request createGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                               @RequestParam(value = userInputParam, required = false) String groupName,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value = groupUidParam, required = false) String interGroupUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu;
        if (!interrupted && !USSDGroupUtil.isValidGroupName(groupName)) {
            menu = groupUtil.invalidGroupNamePrompt(user, groupName, thisSection, createGroupMenu);
        } else {
            String groupUid;
            if (!interrupted) {
                Set<MembershipInfo> members = Sets.newHashSet(new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
                Group group = groupBroker.create(user.getUid(), groupName, null, members, GroupPermissionTemplate.DEFAULT_GROUP, null, null, false, false);
                groupUid = group.getUid();
                safetyEventBroker.setSafetyGroup(user.getUid(), groupUid);
            } else {
                groupUid = interGroupUid;
            }

            cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveSafetyGroupMenu(createGroupMenu, groupUid, null));
            menu = new USSDMenu(getMessage(thisSection, createGroupMenu, promptKey + ".done", user));
            menu.setFreeText(true);
            menu.setNextURI(safetyMenus + addRespondents + doSuffix + "?groupUid=" + groupUid);
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + addRespondents)
    @ResponseBody
    public Request addRespondersPrompt(@RequestParam(phoneNumber) String inputNumber,
                                                      @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveSafetyGroupMenu(addRespondents, groupUid, null));
        return menuBuilder(new USSDMenu(getMessage(thisSection, addRespondents, promptKey, user),
                groupMenuWithId(thisSection, addRespondents + doSuffix, groupUid)));
    }

    @RequestMapping(value = safetyGroupPath + addRespondents + doSuffix)
    @ResponseBody
    public Request addRespondentsToGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
                                         @RequestParam(value = groupUidParam, required = true) String groupUid,
                                         @RequestParam(value = userInputParam, required = true) String userInput,
                                         @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {

        USSDMenu menu;
        final String userResponse = (priorInput == null) ? userInput : priorInput;
        User user = userManager.findByInputNumber(inputNumber);

        if (!"0".equals(userResponse.trim())) {
            menu = groupUtil.addNumbersToExistingGroup(user, groupUid, thisSection, userResponse, addRespondents + doSuffix);
            cacheManager.putUssdMenuForUser(inputNumber, saveSafetyGroupMenu(addRespondents + doSuffix, groupUid, userResponse));
        } else {
            menu = new USSDMenu(getMessage(thisSection, addRespondents, promptKey + ".done", user));
            if (!addressBroker.hasAddress(user.getUid())) {
                menu.addMenuOption(safetyMenus + addAddress, getMessage(thisSection, addRespondents, optionsKey + "address", user));
            }
            menu.addMenuOptions(optionsHomeExit(user, false));
            cacheManager.clearUssdMenuForUser(inputNumber);
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + resetSafetyGroup)
    @ResponseBody
    public Request resetPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, saveSafetyMenuPrompt(resetSafetyGroup));
        Group group = user.getSafetyGroup();

        if (group == null) {
            throw new UnsupportedOperationException("Error! This menu should not be called on a user without a safety group");
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, resetSafetyGroup, promptKey, user));
        if (group.getDescendantEvents().isEmpty() && group.getDescendantTodos().isEmpty()) { // todo : test descendant todo
            menu.addMenuOption(safetyMenus + resetSafetyGroup + doSuffix + "?deactivate=true",
                    getMessage(thisSection, resetSafetyGroup, optionsKey + "deactivate", user));
            menu.addMenuOption(safetyMenus + resetSafetyGroup + doSuffix, getMessage(thisSection, resetSafetyGroup, optionsKey + "active", user));
        } else {
            menu.addMenuOption(safetyMenus + resetSafetyGroup + doSuffix, getMessage(optionsKey + "yes", user));
        }
        menu.addMenuOption(safetyMenus + startMenu, getMessage(optionsKey + "back", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + resetSafetyGroup + doSuffix)
    @ResponseBody
    public Request resetDo(@RequestParam(value = phoneNumber) String inputNumber,
                           @RequestParam(value = "deactivate", required = false) boolean deactivate,
                           @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveSafetyMenuPrompt(resetSafetyGroup + doSuffix));
        if (!interrupted) {
            safetyEventBroker.resetSafetyGroup(user.getUid(), deactivate);
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, resetSafetyGroup, promptKey + ".done", user));
        menu.addMenuOption(safetyMenus + newGroup, getMessage(thisSection, resetSafetyGroup, optionsKey + "create", user));
        menu.addMenuOption(safetyMenus + pickGroup, getMessage(thisSection, resetSafetyGroup, optionsKey + "pick", user));
        menu.addMenuOption(safetyMenus + startMenu, getMessage(thisSection, "group", optionsKey + "home", user));
        return menuBuilder(menu);
    }

    /*
    SECTION: Handling addresses
     */

    @RequestMapping(value = safetyGroupPath + viewAddress)
    @ResponseBody
    public Request viewAddress(@RequestParam String msisdn) throws URISyntaxException {
        final User user = userManager.findByInputNumber(msisdn, saveSafetyMenuPrompt(viewAddress));
        final Address address = addressBroker.getUserAddress(user.getUid());
        final String[] fields = new String[]{address.getHouse(), address.getStreet(), address.getNeighbourhood()};
        final String prompt = StringUtils.isEmpty(address.getNeighbourhood()) ? getMessage(thisSection, viewAddress, promptKey + ".notown", fields, user)
                : getMessage(thisSection, viewAddress, promptKey, fields, user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOption(safetyMenus + changeAddress + doSuffix, getMessage(thisSection, viewAddress, optionsKey + "change", user));
        menu.addMenuOption(safetyMenus + removeAddress, getMessage(thisSection, viewAddress, optionsKey + "remove", user));
        menu.addMenuOption(safetyMenus + startMenu, getMessage(thisSection, "group", optionsKey + "home", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + addAddress)
    @ResponseBody
    public Request addAddress(@RequestParam String msisdn,
                              @RequestParam(value = userInputParam, required = false) String fieldValue,
                              @RequestParam(value = "interrupted", required = false) boolean interrupted,
                              @RequestParam(value = "field", required = false) String field) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, field == null ? saveSafetyMenuPrompt(addAddress) : saveAddressMenu(addAddress, field));
        // note: this will recursively call itself until done
        return menuBuilder(getAddressMenu(field, user, fieldValue, interrupted));
    }

    @RequestMapping(value = safetyGroupPath + changeAddress)
    @ResponseBody
    public Request changeAddressPrompt(@RequestParam String msisdn,
                                       @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn, saveAddressMenu(changeAddress, field));
        USSDMenu menu;
        if ("house".equals(field)) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "house");
        } else if ("street".equals(field)) {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".street", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "street");
        } else {
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".town", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "change-address-do?field=" + "town");
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + changeAddress + doSuffix)
    @ResponseBody
    public Request changeAddressDo(@RequestParam String msisdn,
                                   @RequestParam(value = userInputParam) String fieldValue,
                                   @RequestParam(value = "interrupted", required = false) boolean interrupted,
                                   @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn, saveAddressMenu(changeAddress + doSuffix, field));
        USSDMenu menu;

        if (field !=null && !interrupted) {
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
        final String confirmPrompt = StringUtils.isEmpty(address.getNeighbourhood()) ? getMessage(thisSection, "address.confirm", promptKey + ".notown", confirmFields, user)
                : getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);

        menu = new USSDMenu(confirmPrompt);
        menu.addMenuOption(safetyMenus + startMenu, getMessage(thisSection, "address.confirm", optionsKey + "yes", user));
        menu.addMenuOption(safetyMenus + "change-address?field=house", getMessage(thisSection, "address.confirm", optionsKey + "changehouse", user));
        menu.addMenuOption(safetyMenus + "change-address?field=street", getMessage(thisSection, "address.confirm", optionsKey + "changestreet", user));
        menu.addMenuOption(safetyMenus + "change-address?field=town", getMessage(thisSection, "address.confirm", optionsKey + "changearea", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + removeAddress)
    @ResponseBody
    public Request removeAddress(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, USSDUrlUtil.saveSafetyMenuPrompt(removeAddress));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "address.remove", promptKey, user));
        menu.addMenuOption(safetyMenus + removeAddress + doSuffix, getMessage(thisSection, "address.remove", optionsKey + "yes", user));
        menu.addMenuOption(safetyMenus + viewAddress, getMessage(thisSection, "address.remove", optionsKey + "no", user));
        menu.addMenuOption(safetyMenus + startMenu, getMessage(optionsKey + "back", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = safetyGroupPath + removeAddress + doSuffix)
    @ResponseBody
    public Request removeAddressDo(@RequestParam String msisdn,
                                   @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn, saveSafetyMenuPrompt(removeAddress + doSuffix));
        if (!interrupted) {
            addressBroker.removeAddress(user.getUid());
        }
        USSDMenu menu = new USSDMenu(getMessage(thisSection, removeAddress, promptKey + ".done", user));
        menu.addMenuOption(safetyMenus + addAddress, getMessage(thisSection, removeAddress, optionsKey + "new", user));
        menu.addMenuOption(safetyMenus + startMenu,getMessage(optionsKey + "back", user));
        return menuBuilder(menu);
    }

    /*
    SECTION: Handling responses
     */


    @RequestMapping(value = safetyGroupPath + recordResponse + doSuffix)
    @ResponseBody
    public Request recordResponse(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam(value = entityUidParam) String safetyEventUid,
                                  @RequestParam(value = yesOrNoParam) boolean responded) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        String prompt = (responded) ? getMessage(thisSection, "response", promptKey + ".yes", user) : getMessage(thisSection, "response", promptKey + ".no", user);
        USSDMenu menu = new USSDMenu(prompt);
        if (responded) {
            cacheManager.clearSafetyEventResponseForUser(user, safetyEventBroker.load(safetyEventUid));
            menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=1",
                    getMessage(thisSection, "response", "option.valid", user));
            menu.addMenuOption(thisSection.toPath() + "record-validity-do?entityUid=" + safetyEventUid + "&response=0",
                    getMessage(thisSection, "response", "option.invalid", user));
        } else {
            menu.addMenuOptions(optionsHomeExit(user, false));
        }
        return menuBuilder(menu);

    }

    @RequestMapping(value = safetyGroupPath + recordValidity + doSuffix)
    @ResponseBody
    public Request recordValidity(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = entityUidParam) String safetyEventUid,
                                  @RequestParam("response") boolean validity) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        //only one will be recorded, the cache will be cleared for others
        safetyEventBroker.recordResponse(user.getUid(), safetyEventUid, validity);
        String prompt = getMessage(thisSection, "response", promptKey + ".thanks", user);
        USSDMenu menu = new USSDMenu(prompt);
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menuBuilder(menu);
    }


    private USSDMenu getAddressMenu(String field, User user, String fieldValue, boolean interrupted) {

        USSDMenu menu;

        if (field == null) {
            log.info("field passed as null, so starting at beginning ...");
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".house", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "house");
        } else if ("house".equals(field)) {
            if (!interrupted) {
                addressBroker.updateUserAddress(user.getUid(), fieldValue, null, null);
            }
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".street", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "street");
        } else if ("street".equals(field)) {
            if (!interrupted) {
                addressBroker.updateUserAddress(user.getUid(), null, fieldValue, null);
            }
            menu = new USSDMenu(getMessage(thisSection, "address", promptKey + ".town", user));
            menu.setFreeText(true);
            menu.setNextURI(thisSection.toPath() + "add-address?field=" + "town");
        } else {
            if (!StringUtils.isEmpty(fieldValue) && !"0".equals(fieldValue.trim())) {
                addressBroker.updateUserAddress(user.getUid(), null, null, fieldValue);
            }

            Address address = addressBroker.getUserAddress(user.getUid());
            String confirmPrompt;
            String[] confirmFields;

            if (!StringUtils.isEmpty(address.getNeighbourhood())) {
                confirmFields = new String[]{address.getHouse(), address.getStreet(), address.getNeighbourhood()};
                confirmPrompt = getMessage(thisSection, "address.confirm", promptKey, confirmFields, user);
            } else {
                confirmFields = new String[]{address.getHouse(), address.getStreet() };
                confirmPrompt = getMessage(thisSection, "address.confirm.notown", promptKey, confirmFields, user);
            }

            menu = new USSDMenu(confirmPrompt);
            menu.addMenuOption(safetyMenus + startMenu, getMessage(thisSection, "address.confirm", optionsKey + "yes", user));
            menu.addMenuOption(safetyMenus + "change-address?&field=house", getMessage(thisSection, "address.confirm", optionsKey + "changehouse", user));
            menu.addMenuOption(safetyMenus + "change-address?&field=street", getMessage(thisSection, "address.confirm", optionsKey + "changestreet", user));
            menu.addMenuOption(safetyMenus + "change-address?&field=town", getMessage(thisSection, "address.confirm", optionsKey + "changearea", user));
        }
        return menu;

    }
}