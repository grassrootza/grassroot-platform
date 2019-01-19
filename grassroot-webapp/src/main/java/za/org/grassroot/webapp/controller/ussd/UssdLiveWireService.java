package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdLiveWireService {
	USSDMenu assembleLiveWireOpening(User user, int page);

	Request processLiveWIrePageMenu(String msisdn, int page) throws URISyntaxException;

	Request processSelectContactForMeeting(String msisdn, String mtgUid, String alertUid) throws URISyntaxException;

	Request processSelectGroupForInstantAlert(String msisdn, Integer pageNumber) throws URISyntaxException;

	Request processPromptToRegisterAsContact(String msisdn) throws URISyntaxException;

	Request processRegisterAsLiveWireContact(String msisdn, boolean location) throws URISyntaxException;

	Request processGroupChosen(String msisdn, String groupUid, String alertUid) throws URISyntaxException;

	Request processEnterContactPersonNumber(String msisdn, String alertUid, Boolean revising) throws URISyntaxException;

	Request processEnterContactPersonName(String msisdn, String alertUid, String request, String priorInput, String contactUid, Boolean revising) throws URISyntaxException;

	Request processEnterDescription(String msisdn, String alertUid, String request, String contactUid, String priorInput) throws URISyntaxException;

	Request processChooseList(String msisdn, String alertUid, String request, String priorInput) throws URISyntaxException;

	Request processConfirmAlert(String msisdn, String alertUid, String request, String field, String priorInput, String destType, String destinationUid, String contactUid) throws URISyntaxException;

	Request processSendAlert(String msisdn, String alertUid, boolean location) throws URISyntaxException;
}
