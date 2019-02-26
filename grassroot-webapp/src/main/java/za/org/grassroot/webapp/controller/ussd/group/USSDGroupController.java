package za.org.grassroot.webapp.controller.ussd.group;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;
import static za.org.grassroot.webapp.controller.ussd.group.UssdGroupService.*;

/**
 * @author luke on 2015/08/14.
 */
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDGroupController {

	private final UssdGroupService ussdGroupService;

	private static final String groupPath = homePath + groupMenus;
	private static final String groupUidParam = "groupUid";

	public USSDGroupController(UssdGroupService ussdGroupService) {
		this.ussdGroupService = ussdGroupService;
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
		return ussdGroupService.processGroupPaginationHelper(inputNumber, prompt, pageNumber, existingUri, section, newUri);
	}

	/*
	First menu: display a list of groups, with the option to create a new one
	*/
	@RequestMapping(value = groupPath + startMenu)
	@ResponseBody
	public Request groupList(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							 @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {
		return ussdGroupService.processGroupList(inputNumber, interrupted);

	}

    /*
    Second menu: once the user has selected a group, give them options to name, create join code, add a member, or unsub themselves
     */

	@RequestMapping(value = groupPath + existingGroupMenu)
	@ResponseBody
	public Request groupMenu(@RequestParam(value = phoneNumber) String inputNumber,
							 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
		return ussdGroupService.processGroupMenu(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + advancedGroupMenu)
	@ResponseBody
	public Request advancedGroupMenu(@RequestParam(value = phoneNumber) String inputNumber,
									 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
		return ussdGroupService.processAdvancedGroupMenu(inputNumber, groupUid);
	}

    /*
    The user is creating a group. First, ask for the group name.
     */

	@RequestMapping(value = groupPath + createGroupMenu)
	@ResponseBody
	public Request createPrompt(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
		return ussdGroupService.processCreatePrompt(inputNumber);
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
		return ussdGroupService.processCreateGroupWithName(inputNumber, groupName, interrupted, groupUid);
	}

	@RequestMapping(value = groupPath + "public")
	public Request setGroupPublic(@RequestParam(phoneNumber) String inputNumber, @RequestParam(groupUidParam) String groupUid,
								  @RequestParam boolean useLocation) throws URISyntaxException {
		return ussdGroupService.processSetGroupPublic(inputNumber, groupUid, useLocation);
	}

	@RequestMapping(value = groupPath + "private")
	public Request setGroupPrivate(@RequestParam(phoneNumber) String inputNumber, @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {
		return ussdGroupService.processSetGroupPrivate(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + closeGroupToken)
	@ResponseBody
	public Request closeGroupTokenDo(@RequestParam(phoneNumber) String inputNumber,
									 @RequestParam(groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupService.processCloseGroupTokenDo(inputNumber, groupUid);
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
		return ussdGroupService.processCreateGroupAddNumbersOpeningPrompt(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + createGroupAddNumbers + doSuffix)
	@ResponseBody
	public Request addNumbersToNewlyCreatedGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
												 @RequestParam(value = groupUidParam, required = true) String groupUid,
												 @RequestParam(value = userInputParam, required = true) String userInput,
												 @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException, UnsupportedEncodingException {

		return ussdGroupService.processAddNumbersToNewlyCreatedGroup(inputNumber, groupUid, userInput, priorInput);
	}

	@RequestMapping(value = groupPath + approveUser + doSuffix)
	@ResponseBody
	public Request approveUser(@RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam String requestUid) throws URISyntaxException {

		return ussdGroupService.processApproveUser(inputNumber, requestUid);

	}

	@RequestMapping(value = groupPath + rejectUser + doSuffix)
	@ResponseBody
	public Request rejectUser(@RequestParam(value = phoneNumber) String inputNumber,
							  @RequestParam String requestUid) throws URISyntaxException {

		return ussdGroupService.processRejectUser(inputNumber, requestUid);
	}

	/**
	 * SECTION: MENUS TO ADD MEMBERS, UNSUBSCRIBE, AND LIST MEMBERS
	 */

	@RequestMapping(value = groupPath + addMemberPrompt)
	@ResponseBody
	public Request addNumberInput(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupService.processAddNumberInput(inputNumber, groupUid);
	}

	@RequestMapping(value = groupPath + addMemberPrompt + doSuffix)
	@ResponseBody
	public Request addNumberToGroup(@RequestParam(value = phoneNumber) String inputNumber,
									@RequestParam(value = groupUidParam) String groupUid,
									@RequestParam(value = userInputParam) String numberToAdd) throws URISyntaxException {

		return ussdGroupService.processAddNumberToGroup(inputNumber, groupUid, numberToAdd);
	}

	@RequestMapping(value = groupPath + unsubscribePrompt)
	@ResponseBody
	public Request unsubscribeConfirm(@RequestParam(value = phoneNumber) String inputNumber,
									  @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {

		return ussdGroupService.processUnsubscribeConfirm(inputNumber, groupUid);

	}

	@RequestMapping(value = groupPath + unsubscribePrompt + doSuffix)
	@ResponseBody
	public Request unsubscribeDo(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam(value = groupUidParam) String groupUid) throws URISyntaxException {
		return ussdGroupService.processUnsubscribeDo(inputNumber, groupUid);
	}
}