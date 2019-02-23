package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdSafetyGroupService {
	String
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
			newGroup = "new-group";

	USSDMenu assemblePanicButtonActivationMenu(User user);

	USSDMenu assemblePanicButtonActivationResponse(User user, SafetyEvent safetyEvent);

	Request processManageSafetyGroup(String msisdn) throws URISyntaxException;

	Request processPickSafetyGroup(String msisdn) throws URISyntaxException;

	Request processPickSafetyGroupDo(String msisdn, String groupUid) throws URISyntaxException;

	Request processRequestLocationTracking(String msisdn) throws URISyntaxException;

	Request processApproveLocationTracking(String msisdn) throws URISyntaxException;

	Request processRevokeLocationTracking(String msisdn) throws URISyntaxException;

	Request processCheckCurrentLocation(String msisdn) throws URISyntaxException;

	Request processRespondToCurrentLocation(String msisdn, String addressUid, double latitude, double longitude) throws URISyntaxException;

	Request processChangeCurrentLocation(String msisdn, String addressUid, double latitude, double longitude) throws URISyntaxException;

	Request processDescribeCurrentLocation(String msisdn, String addressUid, double latitude, double longitude, String request) throws URISyntaxException;

	Request processCreateGroup(String inputNumber, String groupName, boolean interrupted, String interGroupUid) throws URISyntaxException;

	Request processAddRespondersPrompt(String inputNumber, String groupUid) throws URISyntaxException;

	Request processAddRespondentsToGroup(String inputNumber, String groupUid, String userInput, String priorInput) throws URISyntaxException;

	Request processResetPrompt(String inputNumber) throws URISyntaxException;

	Request processResetDo(String inputNumber, boolean deactivate, boolean interrupted) throws URISyntaxException;

	Request processViewAddress(String msisdn) throws URISyntaxException;

	Request processAddAddress(String msisdn, String fieldValue, boolean interrupted, String field) throws URISyntaxException;

	Request processChangeAddressPrompt(String msisdn, String field) throws URISyntaxException;

	Request processChangeAddressDo(String msisdn, String fieldValue, boolean interrupted, String field) throws URISyntaxException;

	Request processRemoveAddress(String msisdn) throws URISyntaxException;

	Request processRemoveAddressDo(String msisdn, boolean interrupted) throws URISyntaxException;

	Request processRecordResponse(String inputNumber, String safetyEventUid, boolean responded) throws URISyntaxException;

	Request processRecordValidity(String inputNumber, String safetyEventUid, boolean validity) throws URISyntaxException;

	Request processNewGroup(String inputNumber) throws URISyntaxException;
}
