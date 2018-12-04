package za.org.grassroot.webapp.controller.ussd;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.webapp.util.USSDCampaignConstants;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class USSDCampaignControllerTest extends USSDAbstractUnitTest {

    private static final String testUserPhone = "27801110000";
    private static final String testCode = "234";
    private static final String testLanguage = "en";
    private static final String testMessageUid = "123";
    private static final String path = "/ussd/campaign/";

    private User testUser;
    private UserMinimalProjection testUserMin;

    private Group testGroup;
    private Campaign testCampaign;

    MultiValueMap<String, String> params;

    @InjectMocks
    private USSDCampaignController ussdCampaignController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdCampaignController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdCampaignController);

        testUser = new User(testUserPhone, null, null);
        testUserMin = new UserMinimalProjection(testUser.getUid(), null, null, null);
        testGroup = new Group("test group", testUser);
        testCampaign = createTestCampaign();
        params = new LinkedMultiValueMap<>();
        params.add(USSDCampaignConstants.CAMPAIGN_ID_PARAMETER,testCode);
        params.add(USSDCampaignConstants.LANGUAGE_PARAMETER,testLanguage);
        params.add(USSDUrlUtil.phoneNumber,testUserPhone);
    }

    private MultiValueMap<String, String> getParams(String testMessageUid) {
        MultiValueMap<String, String> newParams = new LinkedMultiValueMap<>(params);
        newParams.add("messageUid", testMessageUid);
        return newParams;
    }

    @Test
    public void testProcessMoreInfoRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.MORE_INFO, "testing_123", Locale.ENGLISH, "English more info message",
                UserInterfaceType.USSD, null);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignConstants.MORE_INFO_URL)
                .params(getParams(testMsg.getUid())));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English more info message"));
    }

    @Test
    public void testSignPetitionRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.MORE_INFO, "testing_123", Locale.ENGLISH, "English Sign petition Message",
                UserInterfaceType.USSD, null);
        when(userManagementServiceMock.findUserMinimalByMsisdn(testUserPhone)).thenReturn(testUserMin);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignConstants.SIGN_PETITION_URL)
                .params(getParams(testMsg.getUid()))).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Sign petition Message. Do you want to keep informed of updates?"));
    }

    @Test
    public void testProcessExitRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.EXIT_NEGATIVE, "testing_123", Locale.ENGLISH, "English Exit Message",
                UserInterfaceType.USSD, null);
        when(userManagementServiceMock.findUserMinimalByMsisdn(testUserPhone)).thenReturn(testUserMin);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignConstants.EXIT_URL).params(getParams(testMsg.getUid()))).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Exit Message"));
    }

    @Test
    public void testProcessJoinMasterGroupRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.JOIN_GROUP, "testing_123", Locale.ENGLISH, "English Join Master group Message",
                UserInterfaceType.USSD, null);
        UserMinimalProjection setProvinceUserMin = new UserMinimalProjection(testUser.getUid(), null, null, Province.ZA_GP);
        when(userManagementServiceMock.findUserMinimalByMsisdn(testUserPhone)).thenReturn(setProvinceUserMin);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        when(campaignBroker.addUserToCampaignMasterGroup(testCampaign.getUid(), testUser.getUid(), UserInterfaceType.USSD)).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignConstants.JOIN_MASTER_GROUP_URL)
                .params(getParams(testMsg.getUid()))).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Join Master group Message. What is your name?"));
    }

    @Test
    public void testProcessTagMeRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.JOIN_GROUP, "testing_123", Locale.ENGLISH, "English Tag me Message",
                UserInterfaceType.USSD, null);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        MultiValueMap<String, String> params = getParams(testMsg.getUid());
        params.add("parentMsgUid", testMsg.getUid());
        ResultActions response = mockMvc.perform(get(path + USSDCampaignConstants.TAG_ME_URL)
                .params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Tag me Message"));
    }

    @Test
    public void testUserSetLanguageForCampaign() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, CampaignActionType.OPENING, "testing_123", Locale.ENGLISH, "First Test English Message",
                UserInterfaceType.USSD, null);
        when(userManagementServiceMock.findByInputNumber(anyString())).thenReturn(testUser);
        when(campaignBroker.getOpeningMessage(testCampaign.getUid(), new Locale(testLanguage),
                UserInterfaceType.USSD, null)).thenReturn(testMsg);
        MultiValueMap<String, String> params = getParams("");
        params.add("campaignUid", testCampaign.getUid());
        ResultActions response = mockMvc.perform(get(path + USSDCampaignConstants.SET_LANGUAGE_URL)
                .params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("First Test English Message"));
    }

    private Campaign createTestCampaign(){
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
