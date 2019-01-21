package za.org.grassroot.webapp.controller.ussd.group;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

// holder for a bunch of relatively straightforward and self-contained group mgmt menus (e.g., renaming etc)
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDGroupMgmtController {

	private static final String groupPath = homePath + groupMenus;

	private final UssdGroupMgmtService ussdGroupMgmtService;

	public USSDGroupMgmtController(UssdGroupMgmtService ussdGroupMgmtService) {
		this.ussdGroupMgmtService = ussdGroupMgmtService;
	}

/*
    Menu options to rename a group, either existing, or if a new group, to give it a name
     */

	@RequestMapping(value = groupPath + "rename")
	@ResponseBody
	public Request renamePrompt(@RequestParam(value = phoneNumber) String inputNumber,
								@RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
		return ussdGroupMgmtService.processRenamePrompt(inputNumber, groupUid);

	}

	@RequestMapping(value = groupPath + "rename-do")
	@ResponseBody
	public Request renameGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							   @RequestParam(value = groupUidParam, required = true) String groupUid,
							   @RequestParam(value = userInputParam, required = true) String newName,
							   @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

		return ussdGroupMgmtService.processRenameGroup(inputNumber, groupUid, newName, interrupted);
	}

	@RequestMapping(value = groupPath + "visibility")
	@ResponseBody
	public Request groupVisibility(@RequestParam(value = phoneNumber) String inputNumber,
								   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupMgmtService.processGroupVisibility(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "visibility" + doSuffix)
	@ResponseBody
	public Request groupVisibilityDo(@RequestParam(value = phoneNumber) String inputNumber, @RequestParam(value = groupUidParam) String groupUid,
									 @RequestParam(value = "hide") boolean isDiscoverable) throws URISyntaxException {

		return ussdGroupMgmtService.processGroupVisibilityDo(inputNumber, groupUid, isDiscoverable);
	}

    /*
    SECTION: MENUS FOR GROUP TOKENS
     */

	@RequestMapping(value = groupPath + "token")
	@ResponseBody
	public Request groupToken(@RequestParam(value = phoneNumber) String inputNumber,
							  @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupMgmtService.processGroupToken(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "token-do")
	@ResponseBody
	public Request createToken(@RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam(value = groupUidParam) String groupUid,
							   @RequestParam(value = "days") Integer daysValid) throws URISyntaxException {
		return ussdGroupMgmtService.processCreateToken(inputNumber, groupUid, daysValid);
	}

	@RequestMapping(value = groupPath + "token-extend")
	@ResponseBody
	public Request extendToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							   @RequestParam(value = groupUidParam, required = true) String groupUid,
							   @RequestParam(value = "days", required = false) Integer daysValid) throws URISyntaxException {

		return ussdGroupMgmtService.processExtendToken(inputNumber, groupUid, daysValid);
	}

	@RequestMapping(value = groupPath + "token-close")
	@ResponseBody
	public Request closeToken(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							  @RequestParam(value = groupUidParam, required = true) String groupUid,
							  @RequestParam(value = yesOrNoParam, required = false) String confirmed) throws URISyntaxException {

		return ussdGroupMgmtService.processCloseToken(inputNumber, groupUid, confirmed);
	}

	/*
   SETTING ALIAS FOR GROUP
	*/
	@RequestMapping(value = groupPath + "alias")
	@ResponseBody
	public Request promptForAlias(@RequestParam(value = phoneNumber) String msisdn, @RequestParam String groupUid) throws URISyntaxException {
		return ussdGroupMgmtService.processPromptForAlias(msisdn, groupUid);
	}

	@RequestMapping(value = groupPath + "alias-do")
	@ResponseBody
	public Request changeAlias(@RequestParam(value = phoneNumber) String msisdn,
							   @RequestParam String groupUid,
							   @RequestParam(value = userInputParam) String input) throws URISyntaxException {
		return ussdGroupMgmtService.processChangeAlias(msisdn, groupUid, input);
	}

	/**
	 * SECTION: GROUP INACTIVE AND LANGUAGE
	 */

	@RequestMapping(value = groupPath + "inactive")
	@ResponseBody
	public Request inactiveConfirm(@RequestParam(value = phoneNumber) String inputNumber,
								   @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupMgmtService.processInactiveConfirm(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "inactive" + doSuffix)
	@ResponseBody
	public Request inactiveDo(@RequestParam(value = phoneNumber) String inputNumber,
							  @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupMgmtService.processInactiveDo(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "list")
	@ResponseBody
	public Request listGroupMemberSize(@RequestParam String msisdn, @RequestParam String groupUid) throws URISyntaxException {
		return ussdGroupMgmtService.processListGroupMemberSize(msisdn, groupUid);
	}

	@RequestMapping(value = groupPath + "sendall")
	@ResponseBody
	public Request sendAllJoinCodesPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdGroupMgmtService.processsEndAllJoinCodesPrompt(inputNumber);
	}

	@RequestMapping(value = groupPath + "send-code")
	@ResponseBody
	public Request sendCreatedGroupJoinCode(@RequestParam(value = phoneNumber) String inputNumber,
											@RequestParam String groupUid) throws URISyntaxException {
		return ussdGroupMgmtService.processsEndCreatedGroupJoinCode(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "/language")
	@ResponseBody
	public Request selectGroupLanguage(@RequestParam(value = phoneNumber) String inputNumber,
									   @RequestParam String groupUid) throws URISyntaxException {
		return ussdGroupMgmtService.processSelectGroupLanguage(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "/language/select")
	@ResponseBody
	public Request confirmGroupLanguage(@RequestParam(value = phoneNumber) String inputNumber,
										@RequestParam String groupUid,
										@RequestParam String language) throws URISyntaxException {
		return ussdGroupMgmtService.processConfirmGroupLanguage(inputNumber, groupUid, language);
	}

	@RequestMapping(value = groupPath + "/organizer")
	@ResponseBody
	public Request addGroupOrganizerPrompt(@RequestParam(value = phoneNumber) String inputNumber,
										   @RequestParam String groupUid) throws URISyntaxException {
		return ussdGroupMgmtService.processAddGroupOrganizerPrompt(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + "/organizer/complete")
	@ResponseBody
	public Request addGroupOrganizerDo(@RequestParam(value = phoneNumber) String inputNumber,
									   @RequestParam String groupUid,
									   @RequestParam(value = userInputParam) String organizerPhone) throws URISyntaxException {
		return ussdGroupMgmtService.processAddGroupOrganizerDo(inputNumber, groupUid, organizerPhone);
	}
}
