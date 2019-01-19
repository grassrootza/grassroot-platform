package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;

public interface UssdCampaignService {
	String campaignMenus = "campaign/";

	Request handleUserSetKanguageForCampaign(String inputNumber, String campaignUid, String languageCode) throws URISyntaxException;

	Request handleMoreInfoRequest(String inputNumber, String messageUid) throws URISyntaxException;

	Request handleTagMeRequest(String inputNumber, String messageUid, String parentMsgUid) throws URISyntaxException;

	Request handleJoinMasterGroupRequest(String inputNumber, String messageUid, String campaignUid) throws URISyntaxException;

	Request handleSetUserJoinTopic(String inputNumber, String campaignUid, String topic) throws URISyntaxException;

	Request handleProvinceRequest(String inputNumber, String campaignUid, Province province) throws URISyntaxException;

	Request handleNameRequest(String inputNumber, String campaignUid, String enteredName) throws URISyntaxException;

	Request handleExitRequest(String inputNumber, String messageUid, String campaignUid) throws URISyntaxException;

	Request handleSharePrompt(String inputNumber, String messageUid) throws URISyntaxException;

	Request handleSignPetitionRequest(String inputNumber, String messageUid) throws URISyntaxException;

	Request handleShareDo(String inputNumber, String userInput, String campaignUid) throws URISyntaxException;
}
