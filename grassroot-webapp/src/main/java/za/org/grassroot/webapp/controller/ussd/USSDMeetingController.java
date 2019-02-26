package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdMeetingService.*;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

/**
 * @author luke on 2015/08/14.
 */
@Slf4j
@RestController
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDMeetingController {

	private final UssdMeetingService ussdMeetingService;

	private static final String path = homePath + meetingMenus;

	@Autowired
	public USSDMeetingController(UssdMeetingService ussdMeetingService) {
		this.ussdMeetingService = ussdMeetingService;
	}

	@RequestMapping(value = path + "respond")
	public Request respondToMtg(@RequestParam(value = phoneNumber) String inputNumber,
								@RequestParam String mtgUid) throws URISyntaxException {
		return ussdMeetingService.processRespondToMtg(inputNumber, mtgUid);
	}

	@RequestMapping(value = path + "description")
	public Request showMeetingDescription(@RequestParam(value = phoneNumber) String inputNumber,
										  @RequestParam String mtgUid) throws URISyntaxException {
		return ussdMeetingService.processShowMeetingDescription(inputNumber, mtgUid);
	}

	@RequestMapping(value = path + "rsvp")
	@ResponseBody
	public Request rsvpAndWelcome(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String meetingUid,
								  @RequestParam(value = yesOrNoParam) String attending) throws URISyntaxException {
		return ussdMeetingService.processRsvpAndWelcome(inputNumber, meetingUid, attending);
	}

	/*
	Helper method for event pagination
	 */
	@RequestMapping(value = homePath + "event_page")
	@ResponseBody
	public Request eventPaginationHelper(@RequestParam(value = phoneNumber) String inputNumber,
										 @RequestParam(value = "section") String section,
										 @RequestParam(value = "prompt") String prompt,
										 @RequestParam(value = "newMenu", required = false) String menuForNew,
										 @RequestParam(value = "newOption", required = false) String optionForNew,
										 @RequestParam(value = "page") Integer pageNumber,
										 @RequestParam(value = "nextUrl") String nextUrl,
										 @RequestParam(value = "pastPresentBoth") Integer pastPresentBoth,
										 @RequestParam(value = "includeGroupName") boolean includeGroupName) throws URISyntaxException {
		return ussdMeetingService.processEventPaginationHelper(inputNumber, section, prompt, menuForNew, optionForNew, pageNumber, nextUrl, pastPresentBoth, includeGroupName);
	}

	/*
	Opening menu for creating. Check if the user has created meetings which are upcoming, and, if so, give options to view and
	manage those. If not, initialize an event and ask them to pick a group or create a new one.
	 */
	@RequestMapping(value = path + startMenu)
	@ResponseBody
	public Request meetingOrg(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							  @RequestParam(value = "newMtg", required = false) boolean newMeeting) throws URISyntaxException, IOException {

		return ussdMeetingService.processMeetingOrg(inputNumber, newMeeting);
	}

	/**
	 * SECTION: Methods and menus for creating a new meeting follow after this
	 * <p>
	 * First, the group handling menus, if the user chose to create a new group, or had no groups, they will go
	 * through the first two menus, which handle number entry and group creation; if they chose an existing group, they
	 * go to the subject input menu. We create the event entity once the group has been completed/selected.
	 */

	@RequestMapping(value = path + newGroupMenu)
	@ResponseBody
	public Request newGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber) throws URISyntaxException {
		return ussdMeetingService.processNewGroup(inputNumber);
	}

	@RequestMapping(value = path + groupName)
	@ResponseBody
	public Request createGroupSetName(@RequestParam(value = phoneNumber, required = true) String inputNumber,
									  @RequestParam(value = userInputParam, required = false) String userResponse
	) throws URISyntaxException {
		return ussdMeetingService.processCreateGroupSetName(inputNumber, userResponse);
	}

    /*
     The most complex handler in this class, as we have four cases, depending on two independent variables:
     a) whether the user has signalled to continue adding numbers or to stop (userResponse.equals('0'))
     b) whether we have a valid group yet or not (groupId is not null)
     the complexity for continuing to add numbers is in the Util class; for stopping, we have to handle here as it
     is specific to this section of menus. Needing to deal with interruptions adds a final layers of complexity.
     Note: if user input is '0' with a valid group, then we mimic the set subject menu and create an event
    */

	@RequestMapping(value = path + groupHandlingMenu)
	@ResponseBody
	public Request createGroup(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							   @RequestParam(value = groupUidParam, required = false) String groupUid,
							   @RequestParam(value = userInputParam, required = false) String userResponse,
							   @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
							   @RequestParam(value = interruptedInput, required = false) String priorInput) throws URISyntaxException {

		return ussdMeetingService.processCreateGroup(inputNumber, groupUid, userResponse, priorInput);
	}

    /*
    The subsequent menus are more straightforward, each filling in a part of the event data structure
     */

	// NOTE: Whatever menu comes after the group selection _has_ to use "groupId" (groupIdParam) instead of "request" (userInputParam),
	// because the menu response will overwrite the 'request' parameter in the returned URL, and we will get a fail on the group
	// note: this is also where we create the event request, as after this we need to keep and store

	@RequestMapping(value = path + subjectMenu)
	@ResponseBody
	public Request getSubject(@RequestParam(value = phoneNumber, required = true) String inputNumber,
							  @RequestParam(value = entityUidParam, required = false) String passedRequestUid,
							  @RequestParam(value = groupUidParam, required = false) String groupUid,
							  @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
							  @RequestParam(value = "revising", required = false) boolean revising) throws URISyntaxException {

		return ussdMeetingService.processGetSubject(inputNumber, passedRequestUid, groupUid, interrupted, revising);
	}

	@RequestMapping(value = path + placeMenu)
	@ResponseBody
	public Request getPlace(@RequestParam(value = phoneNumber) String inputNumber,
							@RequestParam(value = entityUidParam) String mtgRequestUid,
							@RequestParam(value = previousMenu, required = false) String priorMenu,
							@RequestParam(value = userInputParam) String userInput,
							@RequestParam(value = "interrupted", required = false) boolean interrupted,
							@RequestParam(value = "revising", required = false) boolean revising) throws URISyntaxException {

		return ussdMeetingService.processGetPlace(inputNumber, mtgRequestUid, priorMenu, userInput, interrupted, revising);
	}

	@RequestMapping(value = path + timeMenu)
	@ResponseBody
	public Request getTime(@RequestParam(value = phoneNumber) String inputNumber,
						   @RequestParam(value = entityUidParam) String mtgRequestUid,
						   @RequestParam(value = previousMenu, required = false) String priorMenu,
						   @RequestParam(value = userInputParam, required = false) String userInput,
						   @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

		return ussdMeetingService.processGetTime(inputNumber, mtgRequestUid, priorMenu, userInput, interrupted);
	}

	@RequestMapping(value = path + timeOnly)
	@ResponseBody
	public Request changeTimeOnlyMtgReq(@RequestParam(value = phoneNumber, required = true) String inputNumber,
										@RequestParam(value = entityUidParam, required = true) String mtgRequestUid) throws URISyntaxException {

		return ussdMeetingService.processChangeTimeOnlyMtgReq(inputNumber, mtgRequestUid);
	}

	@RequestMapping(value = path + dateOnly)
	@ResponseBody
	public Request changeDateOnlyMtgReq(@RequestParam(value = phoneNumber, required = true) String inputNumber,
										@RequestParam(value = entityUidParam, required = true) String mtgRequestUid) throws URISyntaxException {

		return ussdMeetingService.processChangeDateOnlyMtgReq(inputNumber, mtgRequestUid);
	}

	@RequestMapping(value = path + confirmMenu)
	@ResponseBody
	public Request confirmDetails(@RequestParam(value = phoneNumber, required = true) String inputNumber,
								  @RequestParam(value = entityUidParam, required = true) String mtgRequestUid,
								  @RequestParam(value = previousMenu, required = false) String priorMenu,
								  @RequestParam(value = userInputParam, required = false) String userInput,
								  @RequestParam(value = "interrupted", required = false) boolean interrupted) throws URISyntaxException {

		return ussdMeetingService.processConfirmDetails(inputNumber, mtgRequestUid, priorMenu, userInput, interrupted);
	}

	@RequestMapping(value = path + send)
	@ResponseBody
	public Request sendMessage(@RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam(value = entityUidParam) String mtgRequestUid) throws URISyntaxException {

		return ussdMeetingService.processSendMessage(inputNumber, mtgRequestUid);
	}

	@RequestMapping(value = path + "public")
	public Request makeMtgPublic(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam(value = entityUidParam) String mtgUid,
								 @RequestParam(required = false) Boolean useLocation) throws URISyntaxException {
		return ussdMeetingService.processMakeMtgPublic(inputNumber, mtgUid, useLocation);
	}

	@RequestMapping(value = path + "private")
	public Request makeMtgPrivate(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String mtgUid) throws URISyntaxException {
		return ussdMeetingService.processMakeMtgPrivate(inputNumber, mtgUid);
	}

	/*
	SECTION: Meeting management menus follow after this
   The event management menu -- can't have too much complexity, just giving an RSVP total, and allowing cancel
   and change the date & time or location (other changes have too high a clunky UI vs number of use case trade off)
	*/
	@RequestMapping(value = path + manageMeetingMenu)
	@ResponseBody
	public Request meetingManage(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

		return ussdMeetingService.processMeetingManage(inputNumber, eventUid);
	}

	@RequestMapping(value = path + viewMeetingDetails)
	@ResponseBody
	public Request meetingDetails(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

		return ussdMeetingService.processMeetingDetails(inputNumber, eventUid);
	}

	@RequestMapping(value = path + changeMeetingLocation)
	public Request changeLocation(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String eventUid,
								  @RequestParam(value = requestUidParam, required = false) String requestUid) throws URISyntaxException {
		return ussdMeetingService.processChangeLocation(inputNumber, eventUid, requestUid);
	}


	@RequestMapping(path + newTime)
	public Request newMeetingTime(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String eventUid,
								  @RequestParam(value = requestUidParam, required = false) String requestUid) throws URISyntaxException {

		return ussdMeetingService.processNewMeetingTime(inputNumber, eventUid, requestUid);
	}

	@RequestMapping(path + newDate)
	public Request newMeetingDate(@RequestParam(value = phoneNumber) String inputNumber,
								  @RequestParam(value = entityUidParam) String eventUid,
								  @RequestParam(value = requestUidParam, required = false) String requestUid) throws URISyntaxException {
		return ussdMeetingService.processNewMeetingDate(inputNumber, eventUid, requestUid);
	}

	// note: changing date and time do not come through here, but through a separate menu
	@RequestMapping(value = path + modifyConfirm)
	public Request modifyMeeting(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam(value = entityUidParam) String eventUid,
								 @RequestParam(value = requestUidParam) String requestUid,
								 @RequestParam("action") String action,
								 @RequestParam(userInputParam) String passedInput,
								 @RequestParam(value = "prior_input", required = false) String priorInput) throws URISyntaxException {

		return ussdMeetingService.processModifyMeeting(inputNumber, eventUid, requestUid, action, passedInput, priorInput);
	}

	@RequestMapping(value = path + modifyConfirm + doSuffix)
	public Request modifyMeetingSend(@RequestParam(value = phoneNumber) String inputNumber,
									 @RequestParam(value = entityUidParam) String eventUid,
									 @RequestParam(value = requestUidParam) String requestUid) throws URISyntaxException {

		return ussdMeetingService.processModifyMeetingSend(inputNumber, eventUid, requestUid);
	}

	@RequestMapping(value = path + cancelMeeting)
	public Request meetingCancel(@RequestParam(value = phoneNumber) String inputNumber,
								 @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {
		return ussdMeetingService.processMeetingCancel(inputNumber, eventUid);
	}

	@RequestMapping(value = path + cancelMeeting + doSuffix)
	public Request meetingCancelConfirmed(@RequestParam(value = phoneNumber) String inputNumber,
										  @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {
		return ussdMeetingService.processMeetingCancelConfirmed(inputNumber, eventUid);
	}

	@RequestMapping(value = path + "change-response")
	@ResponseBody
	public Request changeAttendeeResponseDo(@RequestParam(value = phoneNumber) String inputNumber,
											@RequestParam(value = entityUidParam) String eventUid,
											@RequestParam(value = "response") String response) throws URISyntaxException {
		return ussdMeetingService.processChangeAttendeeResponseDo(inputNumber, eventUid, response);

	}
}
