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
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignMessageAction;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.webapp.util.USSDCampaignUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

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
        params.add(USSDCampaignUtil.CODE_PARAMETER,testCode);
        params.add(USSDCampaignUtil.LANGUAGE_PARAMETER,testLanguage);
        params.add(USSDCampaignUtil.MESSAGE_UID,testMessageUid);
        params.add(USSDUrlUtil.phoneNumber,testUserPhone);

    }

    @Test
    public void testProcessMoreInfoRequest() throws Exception {
        when(campaignBroker.getCampaignDetailsByCode(anyString())).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.MORE_INFO_URL).params(params));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English More info Message"));
        response.andExpect(xpath("/request/options/option").string("Sign petition"));

    }

    @Test
    public void testSignPetitionRequest() throws Exception {
        when(campaignBroker.getCampaignDetailsByCode(anyString())).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.SIGN_PETITION_URL).params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Sign petition Message"));
    }

    @Test
    public void testProcessExitRequest() throws Exception {
        when(campaignBroker.getCampaignDetailsByCode(anyString())).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.EXIT_URL).params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Exit Message"));
    }

    @Test
    public void testProcessJoinMasterGroupRequest() throws Exception {
        when(campaignBroker.addUserToCampaignMasterGroup(anyString(),anyString())).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.JOIN_MASTER_GROUP_URL).params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Join Master group Message"));
    }

    @Test
    public void testProcessTagMeRequest() throws Exception {
        when(userManagementServiceMock.loadOrCreateUser(anyString())).thenReturn(testUser);
        when(campaignBroker.getCampaignDetailsByCode(anyString())).thenReturn(testCampaign);
        when(userManagementServiceMock.createUserProfile(any(User.class))).thenReturn(testUser);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.TAG_ME_URL).params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("English Tag me Message"));
    }

    @Test
    public void testUserSetLanguageForCampaign() throws Exception {
        when(userManagementServiceMock.findByInputNumber(anyString())).thenReturn(testUser);
        when(campaignBroker.getCampaignDetailsByCode(anyString())).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get(path + USSDCampaignUtil.SET_LANGUAGE_URL).params(params)).andExpect(status().isOk());
        response.andExpect(status().isOk());
        response.andExpect(content().contentType(MediaType.APPLICATION_XML));
        response.andExpect(xpath("/request/headertext").string("First Test English Message"));
    }

    private Campaign createTestCampaign(){
        Campaign campaign = new Campaign();
        campaign.setUid("2345667890000");
        campaign.setId(1L);
        campaign.setCampaignCode("000");
        campaign.setCampaignDescription("Test Campaign");
        campaign.setStartDateTime(Instant.now());
        campaign.setEndDateTime(Instant.now());
        campaign.setCampaignType(CampaignType.Information);
        campaign.setMasterGroup(testGroup);
        campaign.setCreatedByUser(testUser);
        CampaignMessage englishMessage = new CampaignMessage("First Test English Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);

        CampaignMessage englishMoreInfoMessage = new CampaignMessage("English More info Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);
        CampaignMessage englishSignPetitionAfterMoreReadingMoreInfoMessage = new CampaignMessage("English Sign petition Message 2",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);
        CampaignMessageAction signPetitionActionAfterMoreInfo = new CampaignMessageAction(englishMoreInfoMessage,englishSignPetitionAfterMoreReadingMoreInfoMessage, CampaignActionType.SIGN_PETITION,testUser);
        englishMoreInfoMessage.getCampaignMessageActionSet().add(signPetitionActionAfterMoreInfo);

        CampaignMessage englishSignPetitionMessage = new CampaignMessage("English Sign petition Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);
        CampaignMessage englishExitMessage = new CampaignMessage("English Exit Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);
        CampaignMessage englishJoinMasterGroupMessage = new CampaignMessage("English Join Master group Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);
        CampaignMessage englishTagMeMessage = new CampaignMessage("English Tag me Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.ENGLISH, UserInterfaceType.USSD, campaign);

        englishMessage.setUid(testMessageUid);//set to test one
        CampaignMessageAction moreInfoAction = new CampaignMessageAction(null,englishMoreInfoMessage, CampaignActionType.MORE_INFO,testUser);
        CampaignMessageAction signPetitionAction = new CampaignMessageAction(null,englishSignPetitionMessage, CampaignActionType.SIGN_PETITION,testUser);
        CampaignMessageAction exitAction = new CampaignMessageAction(null,englishExitMessage, CampaignActionType.EXIT,testUser);
        CampaignMessageAction joinGroupAction = new CampaignMessageAction(null,englishJoinMasterGroupMessage, CampaignActionType.JOIN_MASTER_GROUP,testUser);
        CampaignMessageAction tagMeAction = new CampaignMessageAction(null,englishTagMeMessage, CampaignActionType.TAG_ME,testUser);
        Set<CampaignMessageAction> campaignMessageActionSet = new HashSet<>();
        campaignMessageActionSet.add(moreInfoAction);
        campaignMessageActionSet.add(signPetitionAction);
        campaignMessageActionSet.add(exitAction);
        campaignMessageActionSet.add(joinGroupAction);
        campaignMessageActionSet.add(tagMeAction);
        englishMessage.setCampaignMessageActionSet(campaignMessageActionSet);
        CampaignMessage germanMessage = new CampaignMessage("First Test German Message",testUser, MessageVariationAssignment.EXPERIMENT, Locale.GERMAN, UserInterfaceType.USSD, campaign);
        Set<CampaignMessage> campaignMessageSet = new HashSet<>();
        campaignMessageSet.add(englishMessage);
        campaignMessageSet.add(germanMessage);
        campaign.setCampaignMessages(campaignMessageSet);
        return campaign;
    }
}
