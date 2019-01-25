package za.org.grassroot.webapp.controller.ussd.group;

import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdGroupMgmtService {
	Request processRenamePrompt(String inputNumber, String groupUid) throws URISyntaxException;

	Request processRenameGroup(String inputNumber, String groupUid, String newName, boolean interrupted) throws URISyntaxException;

	Request processGroupVisibility(String inputNumber, String groupUid) throws URISyntaxException;

	Request processGroupVisibilityDo(String inputNumber, String groupUid, boolean isDiscoverable) throws URISyntaxException;

	Request processGroupToken(String inputNumber, String groupUid) throws URISyntaxException;

	Request processCreateToken(String inputNumber, String groupUid, Integer daysValid) throws URISyntaxException;

	Request processExtendToken(String inputNumber, String groupUid, Integer daysValid) throws URISyntaxException;

	Request processCloseToken(String inputNumber, String groupUid, String confirmed) throws URISyntaxException;

	Request processPromptForAlias(String msisdn, String groupUid) throws URISyntaxException;

	Request processChangeAlias(String msisdn, String groupUid, String input) throws URISyntaxException;

	Request processInactiveConfirm(String inputNumber, String groupUid) throws URISyntaxException;

	Request processInactiveDo(String inputNumber, String groupUid) throws URISyntaxException;

	Request processListGroupMemberSize(String msisdn, String groupUid) throws URISyntaxException;

	Request processsEndAllJoinCodesPrompt(String inputNumber) throws URISyntaxException;

	Request processsEndCreatedGroupJoinCode(String inputNumber, String groupUid) throws URISyntaxException;

	Request processSelectGroupLanguage(String inputNumber, String groupUid) throws URISyntaxException;

	Request processConfirmGroupLanguage(String inputNumber, String groupUid, String language) throws URISyntaxException;

	Request processAddGroupOrganizerPrompt(String inputNumber, String groupUid) throws URISyntaxException;

	Request processAddGroupOrganizerDo(String inputNumber, String groupUid, String organizerPhone) throws URISyntaxException;
}
