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
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.campaign.CampaignManagerController;
import za.org.grassroot.webapp.model.rest.wrappers.CampaignMessageWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.CampaignWrapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
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

    private List<CampaignWrapper> wrapperList;
    private CampaignWrapper campaignWrapper;
    private List<Campaign> campaignList;
    private Campaign testCampaign;
    private CampaignMessageWrapper campaignMessageWrapper;
    private User testUser;


    @Before
    public void setUp(){

        testUser = new User(testUserPhone);
        campaignList = new ArrayList<>();
        testCampaign = createTestCampaign();
        campaignList.add(testCampaign);
        campaignWrapper = createTestWrapper();
        campaignMessageWrapper = createMessageWrapper();
        wrapperList = new ArrayList<>();
        wrapperList.add(campaignWrapper);
        mockMvc = MockMvcBuilders.standaloneSetup(campaignManagerController).build();
    }


    @Test
    public void testFetchCampaignManagedByUser() throws Exception{
        when(campaignBroker.getCampaignsCreatedByUser(anyString())).thenReturn(campaignList);
        ResultActions response = mockMvc.perform(get("/api/campaign/manage/list/1234"));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());
    }

    @Test
    public void testCreateCampaign() throws Exception{
        when(campaignBroker.getCampaignDetailsByCode(anyString())).thenReturn(null);
        when(campaignBroker.createCampaign(anyString(),anyString(),anyString(),anyString(),any(Instant.class),any(Instant.class), anyList(),any(CampaignType.class),anyString())).thenReturn(testCampaign);
        when(campaignBroker.linkCampaignToMasterGroup(anyString(),anyString(),anyString())).thenReturn(testCampaign);
        when(campaignBroker.createMasterGroupForCampaignAndLinkCampaign(anyString(),anyString(),anyString())).thenReturn(testCampaign);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        String requestJson = mapper.writeValueAsString(campaignWrapper);
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
        String requestJson = mapper.writeValueAsString(campaignMessageWrapper);
        ResultActions response = mockMvc.perform(post("/api/campaign/manage/add/message").contentType(MediaType.APPLICATION_JSON_UTF8).content(requestJson));
        response.andExpect(status().isOk());
        Assert.assertNotNull(response);
    }


    private CampaignWrapper createTestWrapper(){
        CampaignWrapper wrapper = new CampaignWrapper();
        wrapper.setName("Test campaign");
        wrapper.setUrl("www.grassroot.co.za/123");
        wrapper.setStartDate("2017-01-01");
        wrapper.setEndDate("2017-06-01");
        wrapper.setCode("1236");
        wrapper.setDescription("My test campaign");
        return wrapper;
    }

    private Campaign createTestCampaign() {
        Campaign campaign = new Campaign();
        campaign.setUid("2345667890000");
        campaign.setId(1L);
        campaign.setCampaignCode("000");
        campaign.setCampaignDescription("Test Campaign");
        campaign.setMasterGroup(testGroup);
        return campaign;
    }

    private CampaignMessageWrapper createMessageWrapper(){
        CampaignMessageWrapper messageWrapper = new CampaignMessageWrapper();
        messageWrapper.setCampaignCode("123");
        messageWrapper.setAssignmentType("CONTROL");
        messageWrapper.setChannelType(UserInterfaceType.USSD);
        return messageWrapper;
    }

}
