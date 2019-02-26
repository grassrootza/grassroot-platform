package za.org.grassroot.webapp.controller.ussd;


import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

public class UssdCampaignServiceTest extends UssdUnitTest {

	private static final String testUserPhone = "27801110000";
	private static final String testLanguage = "en";
	private static final String testMessageUid = "123";

	private User testUser;
	private UserMinimalProjection testUserMin;
	private Group testGroup;
	private Campaign testCampaign;

	private UssdCampaignService ussdCampaignService;

	@Before
	public void setUp() {
		testUser = new User(testUserPhone, null, null);
		testUserMin = new UserMinimalProjection(testUser.getUid(), null, null, null);
		testGroup = new Group("test group", GroupPermissionTemplate.DEFAULT_GROUP, testUser);
		testCampaign = createTestCampaign();

		this.ussdCampaignService = new UssdCampaignServiceImpl(campaignBrokerMock, userManagementServiceMock, addressBrokerMock, ussdSupport);
	}

	@Test
	public void testProcessMoreInfoRequest() throws Exception {
		CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.MORE_INFO, "testing_123", Locale.ENGLISH, "English more info message",
				UserInterfaceType.USSD, null);
		when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
		when(campaignBrokerMock.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);

		Request request = this.ussdCampaignService.handleMoreInfoRequest(testUserPhone, testMsg.getUid());

		Assert.assertNotNull(request);
		Assertions.assertThat(request.headertext).isEqualTo("English more info message");
	}

	@Test
	public void testSignPetitionRequest() throws Exception {
		CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.MORE_INFO, "testing_123", Locale.ENGLISH, "English Sign petition Message",
				UserInterfaceType.USSD, null);
		when(userManagementServiceMock.findUserMinimalByMsisdn(testUserPhone)).thenReturn(testUserMin);
		when(campaignBrokerMock.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);

		Request request = this.ussdCampaignService.handleSignPetitionRequest(testUserPhone, testMsg.getUid());

		Assert.assertNotNull(request);
		Assertions.assertThat(request.headertext).isEqualTo("English Sign petition Message. Do you want to keep informed of updates?");
	}

	@Test
	public void testProcessExitRequest() throws Exception {
		CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.EXIT_NEGATIVE, "testing_123", Locale.ENGLISH, "English Exit Message",
				UserInterfaceType.USSD, null);
		when(userManagementServiceMock.findUserMinimalByMsisdn(testUserPhone)).thenReturn(testUserMin);
		when(campaignBrokerMock.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);

		Request request = this.ussdCampaignService.handleExitRequest(testUserPhone, testMsg.getUid(), null);

		Assert.assertNotNull(request);
		Assertions.assertThat(request.headertext).isEqualTo("English Exit Message");
	}

	@Test
	public void testProcessJoinMasterGroupRequest() throws Exception {
		CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.JOIN_GROUP, "testing_123", Locale.ENGLISH, "English Join Master group Message",
				UserInterfaceType.USSD, null);
		UserMinimalProjection setProvinceUserMin = new UserMinimalProjection(testUser.getUid(), null, null, Province.ZA_GP);
		when(userManagementServiceMock.findUserMinimalByMsisdn(testUserPhone)).thenReturn(setProvinceUserMin);
		when(campaignBrokerMock.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
		when(campaignBrokerMock.addUserToCampaignMasterGroup(testCampaign.getUid(), testUser.getUid(), UserInterfaceType.USSD)).thenReturn(testCampaign);

		Request request = this.ussdCampaignService.handleJoinMasterGroupRequest(testUserPhone, testMsg.getUid(), null);

		Assert.assertNotNull(request);
		Assertions.assertThat(request.headertext).isEqualTo("English Join Master group Message. What is your name?");
	}

	@Test
	public void testProcessTagMeRequest() throws Exception {
		CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.JOIN_GROUP, "testing_123", Locale.ENGLISH, "English Tag me Message",
				UserInterfaceType.USSD, null);
		when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
		when(campaignBrokerMock.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);

		Request request = this.ussdCampaignService.handleTagMeRequest(testUserPhone, testMsg.getUid(), testMsg.getUid());

		Assert.assertNotNull(request);
		Assertions.assertThat(request.headertext).isEqualTo("English Tag me Message");
	}

	@Test
	public void testUserSetLanguageForCampaign() throws Exception {
		CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "First Test English Message",
				UserInterfaceType.USSD, null);
		when(userManagementServiceMock.findByInputNumber(nullable(String.class))).thenReturn(testUser);
		when(campaignBrokerMock.getOpeningMessage(testCampaign.getUid(), new Locale(testLanguage),
				UserInterfaceType.USSD, null)).thenReturn(testMsg);

		Request request = this.ussdCampaignService.handleUserSetKanguageForCampaign(testUserPhone, testCampaign.getUid(), testLanguage);

		Assert.assertNotNull(request);
		Assertions.assertThat(request.headertext).isEqualTo("First Test English Message");
	}

	private Campaign createTestCampaign() {
		Campaign campaign = new Campaign();
		campaign.setUid("2345667890000");
		campaign.setId(1L);
		campaign.setCampaignCode("000");
		campaign.setDescription("Test Campaign");
		campaign.setStartDateTime(Instant.now());
		campaign.setEndDateTime(Instant.now());
		campaign.setCampaignType(CampaignType.INFORMATION);
		campaign.setMasterGroup(testGroup);
		campaign.setCreatedByUser(testUser);
		CampaignMessage englishMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "First Test English Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);

		CampaignMessage englishMoreInfoMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "English More info Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);
		CampaignMessage englishSignPetitionAfterMoreReadingMoreInfoMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "English Sign petition Message 2", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);
		englishMoreInfoMessage.addNextMessage(englishSignPetitionAfterMoreReadingMoreInfoMessage.getUid(), CampaignActionType.SIGN_PETITION);

		CampaignMessage englishSignPetitionMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "English Sign petition Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);
		CampaignMessage englishExitMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "English Exit Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);
		CampaignMessage englishJoinMasterGroupMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "English Join Master group Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);
		CampaignMessage englishTagMeMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "English Tag me Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);

		englishMessage.setUid(testMessageUid);//set to test one
		englishMessage.addNextMessage(englishMoreInfoMessage.getUid(), CampaignActionType.MORE_INFO);
		englishMessage.addNextMessage(englishSignPetitionMessage.getUid(), CampaignActionType.SIGN_PETITION);
		englishMessage.addNextMessage(englishJoinMasterGroupMessage.getUid(), CampaignActionType.MORE_INFO);
		englishMessage.addNextMessage(englishTagMeMessage.getUid(), CampaignActionType.TAG_ME);

		CampaignMessage germanMessage = new CampaignMessage(testUser, campaign, CampaignActionType.OPENING, "testing_123", Locale.GERMAN, "First Test German Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT);
		Set<CampaignMessage> campaignMessageSet = new HashSet<>();
		campaignMessageSet.add(englishMessage);
		campaignMessageSet.add(germanMessage);
		campaign.setCampaignMessages(campaignMessageSet);
		return campaign;
	}
}
