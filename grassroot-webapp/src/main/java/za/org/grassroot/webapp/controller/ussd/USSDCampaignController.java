package za.org.grassroot.webapp.controller.ussd;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignConstants;

import java.net.URISyntaxException;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.controller.ussd.UssdCampaignService.campaignMenus;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

@RestController
@Slf4j
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDCampaignController {

	private static final String campaignUrl = homePath + campaignMenus;

	private final UssdCampaignService ussdCampaignService;

	public USSDCampaignController(UssdCampaignService ussdCampaignService) {
		this.ussdCampaignService = ussdCampaignService;
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.SET_LANGUAGE_URL)
	@ResponseBody
	public Request userSetLanguageForCampaign(@RequestParam(value = phoneNumber) String inputNumber,
											  @RequestParam String campaignUid,
											  @RequestParam(value = USSDCampaignConstants.LANGUAGE_PARAMETER) String languageCode) throws URISyntaxException {

		return ussdCampaignService.handleUserSetKanguageForCampaign(inputNumber, campaignUid, languageCode);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.MORE_INFO_URL)
	@ResponseBody
	public Request processMoreInfoRequest(@RequestParam(value = phoneNumber) String inputNumber,
										  @RequestParam String messageUid) throws URISyntaxException {
		return ussdCampaignService.handleMoreInfoRequest(inputNumber, messageUid);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.JOIN_MASTER_GROUP_URL)
	@ResponseBody
	public Request processJoinMasterGroupRequest(@RequestParam(value = phoneNumber) String inputNumber,
												 @RequestParam(required = false) String messageUid,
												 @RequestParam(required = false) String campaignUid) throws URISyntaxException {
		return ussdCampaignService.handleJoinMasterGroupRequest(inputNumber, messageUid, campaignUid);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.SIGN_PETITION_URL)
	@ResponseBody
	public Request processSignPetitionRequest(@RequestParam(value = phoneNumber) String inputNumber,
											  @RequestParam String messageUid) throws URISyntaxException {
		return ussdCampaignService.handleSignPetitionRequest(inputNumber, messageUid);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.TAG_ME_URL)
	@ResponseBody
	public Request processTagMeRequest(@RequestParam(value = phoneNumber) String inputNumber,
									   @RequestParam String messageUid,
									   @RequestParam String parentMsgUid) throws URISyntaxException {

		return ussdCampaignService.handleTagMeRequest(inputNumber, messageUid, parentMsgUid);
	}

	@RequestMapping(value = campaignUrl + "topic/set")
	@ResponseBody
	public Request setUserJoinTopic(@RequestParam(value = phoneNumber) String inputNumber,
									@RequestParam String campaignUid,
									@RequestParam String topic) throws URISyntaxException {
		return ussdCampaignService.handleSetUserJoinTopic(inputNumber, campaignUid, topic);
	}

	@RequestMapping(value = campaignUrl + "province")
	public Request processProvinceRequest(@RequestParam(value = phoneNumber) String inputNumber,
										  @RequestParam String campaignUid,
										  @RequestParam Province province) throws URISyntaxException {
		return ussdCampaignService.handleProvinceRequest(inputNumber, campaignUid, province);
	}

	@RequestMapping(value = campaignUrl + "user/name")
	public Request processNameRequest(@RequestParam(value = phoneNumber) String inputNumber,
									  @RequestParam String campaignUid,
									  @RequestParam(value = userInputParam) String enteredName) throws URISyntaxException {
		return ussdCampaignService.handleNameRequest(inputNumber, campaignUid, enteredName);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.EXIT_URL)
	@ResponseBody
	public Request processExitRequest(@RequestParam(value = phoneNumber) String inputNumber,
									  @RequestParam(required = false) String messageUid,
									  @RequestParam(required = false) String campaignUid) throws URISyntaxException {
		return ussdCampaignService.handleExitRequest(inputNumber, messageUid, campaignUid);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.SHARE_URL)
	@ResponseBody
	public Request sharePrompt(@RequestParam(value = phoneNumber) String inputNumber,
							   @RequestParam String messageUid) throws URISyntaxException {
		return ussdCampaignService.handleSharePrompt(inputNumber, messageUid);
	}

	@RequestMapping(value = campaignUrl + USSDCampaignConstants.SHARE_URL + "/do")
	@ResponseBody
	public Request shareDo(@RequestParam(value = phoneNumber) String inputNumber,
						   @RequestParam(value = userInputParam) String userInput,
						   @RequestParam String campaignUid) throws URISyntaxException {
		return ussdCampaignService.handleShareDo(inputNumber, userInput, campaignUid);
	}
}
