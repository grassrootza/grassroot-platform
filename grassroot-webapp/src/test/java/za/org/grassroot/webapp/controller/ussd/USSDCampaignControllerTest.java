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
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.webapp.util.USSDCampaignUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.mockito.Matchers.*;
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
        testGroup = new Group("test group", testUser);
        testCampaign = createTestCampaign();
        params = new LinkedMultiValueMap<>();
        params.add(USSDCampaignUtil.CAMPAIGN_ID_PARAMETER,testCode);
        params.add(USSDCampaignUtil.LANGUAGE_PARAMETER,testLanguage);
        params.add(USSDUrlUtil.phoneNumber,testUserPhone);
    }

    private MultiValueMap<String, String> getParams(String testMessageUid) {
        MultiValueMap<String, String> newParams = new LinkedMultiValueMap<>(params);
        newParams.add("messageUid", testMessageUid);
        return newParams;
    }

    @Test
    public void testProcessMoreInfoRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, Locale.ENGLISH, "English more info message",
                UserInterfaceType.USSD, null, CampaignActionType.MORE_INFO);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.MORE_INFO_URL)
                .params(getParams(testMsg.getUid())));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English more info message"));
    }

    @Test
    public void testSignPetitionRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, Locale.ENGLISH, "English Sign petition Message",
                UserInterfaceType.USSD, null, CampaignActionType.MORE_INFO);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.SIGN_PETITION_URL)
                .params(getParams(testMsg.getUid()))).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Sign petition Message"));
    }

    @Test
    public void testProcessExitRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, Locale.ENGLISH, "English Exit Message",
                UserInterfaceType.USSD, null, CampaignActionType.EXIT_NEGATIVE);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.EXIT_URL).params(getParams(testMsg.getUid()))).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Exit Message"));
    }

    @Test
    public void testProcessJoinMasterGroupRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, Locale.ENGLISH, "English Join Master group Message",
                UserInterfaceType.USSD, null, CampaignActionType.JOIN_MASTER_GROUP);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.JOIN_MASTER_GROUP_URL)
                .params(getParams(testMsg.getUid()))).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Join Master group Message"));
    }

    @Test
    public void testProcessTagMeRequest() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, Locale.ENGLISH, "English Tag me Message",
                UserInterfaceType.USSD, null, CampaignActionType.JOIN_MASTER_GROUP);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(campaignBroker.loadCampaignMessage(testMsg.getUid(), testUser.getUid())).thenReturn(testMsg);
        MultiValueMap<String, String> params = getParams(testMsg.getUid());
        params.add("parentMsgUid", testMsg.getUid());
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.TAG_ME_URL)
                .params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Tag me Message"));
    }

    @Test
    public void testUserSetLanguageForCampaign() throws Exception {
        CampaignMessage testMsg = new CampaignMessage(testUser, testCampaign, Locale.ENGLISH, "First Test English Message",
                UserInterfaceType.USSD, null, CampaignActionType.OPENING);
        when(userManagementServiceMock.findByInputNumber(anyString())).thenReturn(testUser);
        when(campaignBroker.getOpeningMessage(testCampaign.getUid(), new Locale(testLanguage),
                UserInterfaceType.USSD, null)).thenReturn(testMsg);
        MultiValueMap<String, String> params = getParams("");
        params.add("campaignUid", testCampaign.getUid());
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.SET_LANGUAGE_URL)
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
        CampaignMessage englishMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "First Test English Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);

        CampaignMessage englishMoreInfoMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "English More info Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);
        CampaignMessage englishSignPetitionAfterMoreReadingMoreInfoMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "English Sign petition Message 2", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);
        CampaignMessageAction signPetitionActionAfterMoreInfo = new CampaignMessageAction(englishMoreInfoMessage,englishSignPetitionAfterMoreReadingMoreInfoMessage, CampaignActionType.SIGN_PETITION,testUser);
        englishMoreInfoMessage.getCampaignMessageActionSet().add(signPetitionActionAfterMoreInfo);

        CampaignMessage englishSignPetitionMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "English Sign petition Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);
        CampaignMessage englishExitMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "English Exit Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);
        CampaignMessage englishJoinMasterGroupMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "English Join Master group Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);
        CampaignMessage englishTagMeMessage = new CampaignMessage(testUser, campaign, Locale.ENGLISH, "English Tag me Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);

        englishMessage.setUid(testMessageUid);//set to test one
        CampaignMessageAction moreInfoAction = new CampaignMessageAction(null,englishMoreInfoMessage, CampaignActionType.MORE_INFO,testUser);
        CampaignMessageAction signPetitionAction = new CampaignMessageAction(null,englishSignPetitionMessage, CampaignActionType.SIGN_PETITION,testUser);
        CampaignMessageAction exitAction = new CampaignMessageAction(null,englishExitMessage, CampaignActionType.EXIT_NEGATIVE,testUser);
        CampaignMessageAction joinGroupAction = new CampaignMessageAction(null,englishJoinMasterGroupMessage, CampaignActionType.JOIN_MASTER_GROUP,testUser);
        CampaignMessageAction tagMeAction = new CampaignMessageAction(null,englishTagMeMessage, CampaignActionType.TAG_ME,testUser);
        Set<CampaignMessageAction> campaignMessageActionSet = new HashSet<>();
        campaignMessageActionSet.add(moreInfoAction);
        campaignMessageActionSet.add(signPetitionAction);
        campaignMessageActionSet.add(exitAction);
        campaignMessageActionSet.add(joinGroupAction);
        campaignMessageActionSet.add(tagMeAction);
        englishMessage.setCampaignMessageActionSet(campaignMessageActionSet);
        CampaignMessage germanMessage = new CampaignMessage(testUser, campaign, Locale.GERMAN, "First Test German Message", UserInterfaceType.USSD, MessageVariationAssignment.EXPERIMENT, CampaignActionType.OPENING);
        Set<CampaignMessage> campaignMessageSet = new HashSet<>();
        campaignMessageSet.add(englishMessage);
        campaignMessageSet.add(germanMessage);
        campaign.setCampaignMessages(campaignMessageSet);
        return campaign;
    }
}
