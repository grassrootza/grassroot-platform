package za.org.grassroot.webapp.controller.rest;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.campaign.CampaignManagerController;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignMessageActionRequest;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignMessageRequest;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CampaignManagerControllerTest extends RestAbstractUnitTest{

    private static final String testUserPhone = "27801110000";

    @Mock
    private CampaignManagerController campaignManagerController;
    @Mock
    private CampaignBroker campaignBroker;
    @Mock
    private UserManagementService userManager;

    private List<CreateCampaignRequest> wrapperList;
    private CreateCampaignRequest createCampaignRequest;
    private List<Campaign> campaignList;
    private Campaign testCampaign;
    private CreateCampaignMessageRequest createCampaignMessageRequest;
    private CreateCampaignMessageActionRequest createCampaignMessageActionRequest;
    private User testUser;


    @Before
    public void setUp(){

        testUser = new User(testUserPhone, null, null);
        campaignList = new ArrayList<>();
        testCampaign = createTestCampaign();
        campaignList.add(testCampaign);
        createCampaignRequest = createTestWrapper();
        createCampaignMessageRequest = createMessageWrapper();
        createCampaignMessageActionRequest = createCampaignMessageActionWrapper();
        wrapperList = new ArrayList<>();
        wrapperList.add(createCampaignRequest);
        mockMvc = MockMvcBuilders.standaloneSetup(campaignManagerController).build();
    }


    @Test
    public void testFetchCampaignManagedByUser() throws Exception{
        when(campaignBroker.getCampaignsCreatedByUser(anyString())).thenReturn(campaignList);
        ResultActions response = mockMvc.perform(get("/api/campaign/manage/list?userUid=1234"));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());
    }

    @Test
    public void testCreateCampaign() throws Exception{
        when(campaignBroker.getCampaignDetailsByCode(anyString(), eq(null), eq(false))).thenReturn(null);
        when(campaignBroker.create(anyString(), anyString(),anyString(),anyString(), anyString(), any(Instant.class), any(Instant.class), anyList(),any(CampaignType.class),anyString())).thenReturn(testCampaign);
        when(campaignBroker.updateMasterGroup(anyString(),anyString(),anyString())).thenReturn(testCampaign);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        String requestJson = mapper.writeValueAsString(createCampaignRequest);
        ResultActions response = mockMvc.perform(post("/api/campaign/manage/create").contentType(MediaType.APPLICATION_JSON_UTF8).content(requestJson));
        response.andExpect(status().isOk());
        Assert.assertNotNull(response);
    }

    @Test
    public void testAddCampaignTag() throws Exception{
        when(campaignBroker.addCampaignTags(anyString(),anyList())).thenReturn(testCampaign);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("campaignCode","1234");
        params.add("tag","testTag");
        ResultActions response = mockMvc.perform(get("/api/campaign/manage/add/tag").params(params));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());

    }

    @Test
    public void testAddCampaignMessage() throws Exception{
        when(campaignBroker.addCampaignMessage(anyString(),anyString(),any(Locale.class),any(MessageVariationAssignment.class),any(UserInterfaceType.class),any(User.class),anyList())).thenReturn(testCampaign);
        when(userManager.load(anyString())).thenReturn(testUser);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        String requestJson = mapper.writeValueAsString(createCampaignMessageRequest);
        ResultActions response = mockMvc.perform(post("/api/campaign/manage/messages/add/" + testCampaign.getUid())
                .contentType(MediaType.APPLICATION_JSON_UTF8).content(requestJson));
        response.andExpect(status().isOk());
        Assert.assertNotNull(response);
    }

    @Test
    public void testAddMessageAction() throws Exception{
        when(campaignBroker.addActionToCampaignMessage(anyString(),anyString(),any(CampaignActionType.class),anyString(),any(Locale.class),any(MessageVariationAssignment.class),any(UserInterfaceType.class),any(User.class),anySet())).thenReturn(testCampaign);
        when(userManager.load(anyString())).thenReturn(testUser);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        String requestJson = mapper.writeValueAsString(createCampaignMessageActionRequest);
        ResultActions response = mockMvc.perform(post("/api/campaign/manage/messages/action/add/" + testCampaign.getUid())
                .contentType(MediaType.APPLICATION_JSON_UTF8).content(requestJson));
        response.andExpect(status().isOk());
        Assert.assertNotNull(response);
    }


    private CreateCampaignRequest createTestWrapper(){
        CreateCampaignRequest wrapper = new CreateCampaignRequest();
        wrapper.setName("Test campaign");
        wrapper.setUrl("www.grassroot.co.za/123");
        wrapper.setStartDateEpochMillis(LocalDate.parse("2017-01-01").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        wrapper.setEndDateEpochMillis(LocalDate.parse("2017-06-01").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        wrapper.setCode("1236");
        wrapper.setDescription("My test campaign");
        return wrapper;
    }

    private Campaign createTestCampaign() {
        Campaign campaign = new Campaign();
        campaign.setUid("2345667890000");
        campaign.setId(1L);
        campaign.setCampaignCode("000");
        campaign.setDescription("Test Campaign");
        campaign.setMasterGroup(testGroup);
        return campaign;
    }

    private CreateCampaignMessageRequest createMessageWrapper(){
        CreateCampaignMessageRequest messageWrapper = new CreateCampaignMessageRequest();
        messageWrapper.setCampaignCode("123");
        messageWrapper.setAssignmentType(MessageVariationAssignment.CONTROL);
        messageWrapper.setChannelType(UserInterfaceType.USSD);
        messageWrapper.setLanguage(Locale.ENGLISH);
        messageWrapper.setMessage("test message");
        return messageWrapper;
    }

    private CreateCampaignMessageActionRequest createCampaignMessageActionWrapper(){
        CreateCampaignMessageActionRequest wrapper = new CreateCampaignMessageActionRequest();
        wrapper.setCampaignCode("123");
        wrapper.setAction(CampaignActionType.SIGN_PETITION);
        wrapper.setMessageUid("234-567-88");
        wrapper.setUserUid("23456");
        wrapper.setActionMessage(createMessageWrapper());
        return wrapper;
    }

}
