package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSafetyGroupService.*;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

/**
 * Created by paballo on 2016/07/13.
 */

@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDSafetyGroupController {

	private final UssdSafetyGroupService ussdSafetyGroupService;

	private static final String safetyGroupPath = homePath + safetyGroup + "/";
	private static final String groupUidParam = "groupUid";

	@Autowired
	public USSDSafetyGroupController(UssdSafetyGroupService ussdSafetyGroupService) {
		this.ussdSafetyGroupService = ussdSafetyGroupService;
	}

	@RequestMapping(value = safetyGroupPath + startMenu)
	@ResponseBody
	public Request manageSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processManageSafetyGroup(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + pickGroup)
	@ResponseBody
	public Request pickSafetyGroup(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processPickSafetyGroup(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + pickGroup + doSuffix)
	@ResponseBody
	public Request pickSafetyGroupDo(@RequestParam String msisdn, @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
		return ussdSafetyGroupService.processPickSafetyGroupDo(msisdn, groupUid);
	}

    /*
    SECTION: Request and grant permission to track location
     */

	@RequestMapping(value = safetyGroupPath + "location/request")
	@ResponseBody
	public Request requestLocationTracking(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processRequestLocationTracking(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + "location/request/allowed")
	@ResponseBody
	public Request approveLocationTracking(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processApproveLocationTracking(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + "location/revoke")
	@ResponseBody
	public Request revokeLocationTracking(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processRevokeLocationTracking(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + "location/current")
	public Request checkCurrentLocation(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processCheckCurrentLocation(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + "location/current/confirm")
	public Request respondToCurrentLocation(@RequestParam String msisdn, @RequestParam String addressUid,
											@RequestParam double latitude, @RequestParam double longitude) throws URISyntaxException {
		return ussdSafetyGroupService.processRespondToCurrentLocation(msisdn, addressUid, latitude, longitude);
	}

	@RequestMapping(value = safetyGroupPath + "location/current/change")
	public Request changeCurrentLocation(@RequestParam String msisdn, @RequestParam String addressUid,
										 @RequestParam double latitude, @RequestParam double longitude) throws URISyntaxException {
		return ussdSafetyGroupService.processChangeCurrentLocation(msisdn, addressUid, latitude, longitude);
	}

	@RequestMapping(value = safetyGroupPath + "location/current/describe")
	public Request describeCurrentLocation(@RequestParam String msisdn, @RequestParam String addressUid,
										   @RequestParam double latitude, @RequestParam double longitude,
										   @RequestParam String request) throws URISyntaxException {
		return ussdSafetyGroupService.processDescribeCurrentLocation(msisdn, addressUid, latitude, longitude, request);
	}

    /*
    SECTION: Creating a safety group
     */

	@RequestMapping(value = safetyGroupPath + newGroup)
	@ResponseBody
	public Request newGroup(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdSafetyGroupService.processNewGroup(inputNumber);
	}

	@RequestMapping(value = safetyGroupPath + createGroupMenu)
	@ResponseBody
	public Request createGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							   @RequestParam(value = userInputParam, required = false) String groupName,
							   @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
							   @RequestParam(value = groupUidParam, required = false) String interGroupUid) throws URISyntaxException {

		return ussdSafetyGroupService.processCreateGroup(inputNumber, groupName, interrupted, interGroupUid);
	}

	@RequestMapping(value = safetyGroupPath + addRespondents)
	@ResponseBody
	public Request addRespondersPrompt(@RequestParam(phoneNumber) String inputNumber,
									   @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
		return ussdSafetyGroupService.processAddRespondersPrompt(inputNumber, groupUid);
	}

	@RequestMapping(value = safetyGroupPath + addRespondents + doSuffix)
	@ResponseBody
	public Request addRespondentsToGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
										 @RequestParam(value = groupUidParam, required = true) String groupUid,
										 @RequestParam(value = userInputParam, required = true) String userInput,
										 @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {

		return ussdSafetyGroupService.processAddRespondentsToGroup(inputNumber, groupUid, userInput, priorInput);
	}

	@RequestMapping(value = safetyGroupPath + resetSafetyGroup)
	@ResponseBody
	public Request resetPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdSafetyGroupService.processResetPrompt(inputNumber);
	}

	@RequestMapping(value = safetyGroupPath + resetSafetyGroup + doSuffix)
	@ResponseBody
	public Request resetDo(@RequestParam(value = phoneNumber) String inputNumber,
						   @RequestParam(value = "deactivate", required = false) boolean deactivate,
						   @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

		return ussdSafetyGroupService.processResetDo(inputNumber, deactivate, interrupted);
	}

    /*
    SECTION: Handling addresses
     */

	@RequestMapping(value = safetyGroupPath + viewAddress)
	@ResponseBody
	public Request viewAddress(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processViewAddress(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + addAddress)
	@ResponseBody
	public Request addAddress(@RequestParam String msisdn,
							  @RequestParam(value = userInputParam, required = false) String fieldValue,
							  @RequestParam(value = "interrupted", required = false) boolean interrupted,
							  @RequestParam(value = "field", required = false) String field) throws URISyntaxException {
		return ussdSafetyGroupService.processAddAddress(msisdn, fieldValue, interrupted, field);
	}

	@RequestMapping(value = safetyGroupPath + changeAddress)
	@ResponseBody
	public Request changeAddressPrompt(@RequestParam String msisdn,
									   @RequestParam(value = "field", required = false) String field) throws URISyntaxException {
		return ussdSafetyGroupService.processChangeAddressPrompt(msisdn, field);
	}

	@RequestMapping(value = safetyGroupPath + changeAddress + doSuffix)
	@ResponseBody
	public Request changeAddressDo(@RequestParam String msisdn,
								   @RequestParam(value = userInputParam) String fieldValue,
								   @RequestParam(value = "interrupted", required = false) boolean interrupted,
								   @RequestParam(value = "field", required = false) String field) throws URISyntaxException {

		return ussdSafetyGroupService.processChangeAddressDo(msisdn, fieldValue, interrupted, field);
	}

	@RequestMapping(value = safetyGroupPath + removeAddress)
	@ResponseBody
	public Request removeAddress(@RequestParam String msisdn) throws URISyntaxException {
		return ussdSafetyGroupService.processRemoveAddress(msisdn);
	}

	@RequestMapping(value = safetyGroupPath + removeAddress + doSuffix)
	@ResponseBody
	public Request removeAddressDo(@RequestParam String msisdn,
								   @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {
		return ussdSafetyGroupService.processRemoveAddressDo(msisdn, interrupted);
	}

    /*
    SECTION: Handling responses
     */

	@RequestMapping(value = safetyGroupPath + recordResponse + doSuffix)
	@ResponseBody
	public Request recordResponse(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String safetyEventUid,
								  @RequestParam(value = yesOrNoParam) boolean responded) throws URISyntaxException {

		return ussdSafetyGroupService.processRecordResponse(inputNumber, safetyEventUid, responded);
	}

	@RequestMapping(value = safetyGroupPath + recordValidity + doSuffix)
	@ResponseBody
	public Request recordValidity(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = entityUidParam) String safetyEventUid,
								  @RequestParam("response") boolean validity) throws URISyntaxException {

		return ussdSafetyGroupService.processRecordValidity(inputNumber, safetyEventUid, validity);
	}
}