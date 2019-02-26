package za.org.grassroot.webapp.controller.rest;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import net.sf.ehcache.CacheManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.dto.CampaignLogsDataCollection;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignStatsBroker;
import za.org.grassroot.webapp.controller.rest.campaign.CampaignManagerController;
import za.org.grassroot.webapp.model.rest.wrappers.CreateCampaignRequest;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CampaignManagerControllerTest extends RestAbstractUnitTest {

    @Mock
    private JwtService jwtServiceMock;

    @Mock
    private CampaignBroker campaignBroker;

    @Mock
    private CampaignStatsBroker campaignStatsBroker;

    private List<CreateCampaignRequest> wrapperList;
    private CreateCampaignRequest createCampaignRequest;
    private List<Campaign> campaignList;
    private Campaign testCampaign;

    @Before
    public void setUp() throws IOException {

        campaignList = new ArrayList<>();
        testCampaign = createTestCampaign();
        campaignList.add(testCampaign);
        createCampaignRequest = constructCreateCampaignRequest();
        wrapperList = new ArrayList<>();
        wrapperList.add(createCampaignRequest);

        CacheManager cacheManager = constructCacheManager();

        CampaignManagerController campaignManagerController = new CampaignManagerController(jwtServiceMock, campaignBroker, null, campaignStatsBroker, userManagementServiceMock, groupBrokerMock, cacheManager);
        mockMvc = MockMvcBuilders.standaloneSetup(campaignManagerController).build();
    }

    private CacheManager constructCacheManager() throws IOException {
        // this is done by Spring for real
        EhCacheManagerFactoryBean ehCacheManagerFactoryBean = ehCacheManager();
        ehCacheManagerFactoryBean.afterPropertiesSet();
        return ehCacheManagerFactoryBean.getObject();
    }

    private EhCacheManagerFactoryBean ehCacheManager() throws IOException {
   		EhCacheManagerFactoryBean factory = new EhCacheManagerFactoryBean();
   		factory.setCacheManagerName(CacheManager.DEFAULT_NAME);
   		factory.setShared(true);
   		return factory;
   	}

    @Test
    public void testFetchCampaignManagedByUser() throws Exception{
        when(campaignStatsBroker.getCampaignLogData(nullable(String.class))).thenReturn(CampaignLogsDataCollection.builder().build());
        when(campaignBroker.getCampaignsManagedByUser(nullable(String.class))).thenReturn(campaignList);
        when(campaignBroker.load(nullable(String.class))).thenReturn(testCampaign);
        ResultActions response = mockMvc.perform(get("/v2/api/campaign/manage/list?userUid=1234"));
        Assert.assertNotNull(response);
        response.andExpect(status().isOk());
    }

    @Test
    public void testCreateCampaign() throws Exception{
        when(jwtServiceMock.getUserIdFromJwtToken("jwt_token")).thenReturn(sessionTestUser.getUid());
        when(campaignStatsBroker.getCampaignLogData(nullable(String.class))).thenReturn(CampaignLogsDataCollection.builder().build());
        when(campaignBroker.create(nullable(String.class), nullable(String.class),nullable(String.class),nullable(String.class), nullable(String.class), any(Instant.class), any(Instant.class), nullable(List.class),any(CampaignType.class),nullable(String.class), nullable(Boolean.class), nullable(Long.class), nullable(String.class))).thenReturn(testCampaign);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        String requestJson = mapper.writeValueAsString(createCampaignRequest);
        ResultActions response = mockMvc.perform(MockMvcRequestBuilders
                .post("/v2/api/campaign/manage/create")
                .header("Authorization", "Bearer jwt_token")
                .contentType(MediaType.APPLICATION_JSON_UTF8).content(requestJson));
        response.andExpect(status().isOk());
        Assert.assertNotNull(response);
    }

    private CreateCampaignRequest constructCreateCampaignRequest(){
        CreateCampaignRequest wrapper = new CreateCampaignRequest();
        wrapper.setName("Test campaign");
        wrapper.setGroupUid("someGroupUid");
        wrapper.setType(CampaignType.INFORMATION);
        wrapper.setUrl("www.grassroot.co.za/123");
        wrapper.setStartDateEpochMillis(LocalDate.parse("2017-01-01").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        wrapper.setEndDateEpochMillis(LocalDate.parse("2017-06-01").atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli());
        wrapper.setCode("1236");
        wrapper.setGroupName("someGroupName");
        wrapper.setDescription("My test campaign");
        return wrapper;
    }

    private Campaign createTestCampaign() {
        Campaign campaign = new Campaign();
        campaign.setUid("2345667890000");
        campaign.setId(1L);
        Account account = new Account(sessionTestUser, "someAccName", AccountType.STANDARD, sessionTestUser);
        account.setFreeFormCost(10);
        campaign.setAccount(account);
        campaign.setCampaignCode("000");
        campaign.setDescription("Test Campaign");
        campaign.setMasterGroup(testGroup);
        campaign.setCreatedByUser(sessionTestUser);
        return campaign;
    }
}
