package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdMeetingService {
	USSDSection thisSection = USSDSection.MEETINGS;
	String
			newGroupMenu = "newgroup",
			groupName = "groupName",
			groupHandlingMenu = "group",
			subjectMenu = "subject",
			timeMenu = "time",
			placeMenu = "place",
			confirmMenu = "confirm",
			timeOnly = "time_only",
			dateOnly = "date_only",
			send = "send";

	String
			manageMeetingMenu = "manage",
			viewMeetingDetails = "details",
			changeMeetingLocation = "changeLocation",
			cancelMeeting = "cancel",
			newTime = "new_time",
			newDate = "new_date",
			modifyConfirm = "modify";

	String requestUidParam = "requestUid";

	USSDMenu assembleRsvpMenu(User user, Event meeting);

	Request processRespondToMtg(String inputNumber, String mtgUid) throws URISyntaxException;

	Request processShowMeetingDescription(String inputNumber, String mtgUid) throws URISyntaxException;

	Request processRsvpAndWelcome(String inputNumber, String meetingUid, String attending) throws URISyntaxException;

	Request processEventPaginationHelper(String inputNumber, String section, String prompt, String menuForNew, String optionForNew, Integer pageNumber, String nextUrl, Integer pastPresentBoth, boolean includeGroupName) throws URISyntaxException;

	Request processMeetingOrg(String inputNumber, boolean newMeeting) throws URISyntaxException;

	Request processNewGroup(String inputNumber) throws URISyntaxException;

	Request processCreateGroupSetName(String inputNumber, String userResponse) throws URISyntaxException;

	Request processCreateGroup(String inputNumber, String groupUid, String userResponse, String priorInput) throws URISyntaxException;

	Request processGetSubject(String inputNumber, String passedRequestUid, String groupUid, boolean interrupted, boolean revising) throws URISyntaxException;

	Request processGetPlace(String inputNumber, String mtgRequestUid, String priorMenu, String userInput, boolean interrupted, boolean revising) throws URISyntaxException;

	Request processGetTime(String inputNumber, String mtgRequestUid, String priorMenu, String userInput, boolean interrupted) throws URISyntaxException;

	Request processChangeTimeOnlyMtgReq(String inputNumber, String mtgRequestUid) throws URISyntaxException;

	Request processChangeDateOnlyMtgReq(String inputNumber, String mtgRequestUid) throws URISyntaxException;

	Request processConfirmDetails(String inputNumber, String mtgRequestUid, String priorMenu, String userInput, boolean interrupted) throws URISyntaxException;

	Request processSendMessage(String inputNumber, String mtgRequestUid) throws URISyntaxException;

	Request processMakeMtgPublic(String inputNumber, String mtgUid, Boolean useLocation) throws URISyntaxException;

	Request processMakeMtgPrivate(String inputNumber, String mtgUid) throws URISyntaxException;

	Request processMeetingManage(String inputNumber, String eventUid) throws URISyntaxException;

	Request processMeetingDetails(String inputNumber, String eventUid) throws URISyntaxException;

	Request processChangeLocation(String inputNumber, String eventUid, String requestUid) throws URISyntaxException;

	Request processNewMeetingDate(String inputNumber, String eventUid, String requestUid) throws URISyntaxException;

	Request processNewMeetingTime(String inputNumber, String eventUid, String requestUid) throws URISyntaxException;

	Request processModifyMeeting(String inputNumber, String eventUid, String requestUid, String action, String passedInput, String priorInput) throws URISyntaxException;

	Request processModifyMeetingSend(String inputNumber, String eventUid, String requestUid) throws URISyntaxException;

	Request processMeetingCancel(String inputNumber, String eventUid) throws URISyntaxException;

	Request processMeetingCancelConfirmed(String inputNumber, String eventUid) throws URISyntaxException;

	Request processChangeAttendeeResponseDo(String inputNumber, String eventUid, String response) throws URISyntaxException;
}
