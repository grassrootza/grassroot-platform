package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdAdvancedHomeService {
	Request processMoreOptions(String msisdn) throws URISyntaxException;

	Request processGetPublicMeetingNearUser(String inputNumber, Integer page, boolean repeat) throws URISyntaxException;

	Request processMeetingDetails(String inputNumber, String meetingUid) throws URISyntaxException;

	Request processTrackMe(String inputNumber) throws URISyntaxException;
}
