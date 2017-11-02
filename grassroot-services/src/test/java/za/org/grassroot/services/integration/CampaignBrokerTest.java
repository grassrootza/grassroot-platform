package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.campaign.CampaignBroker;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@Transactional
@WithMockUser(username = "0605550000", roles={"SYSTEM_ADMIN"})
public class CampaignBrokerTest {

    @Autowired
    private CampaignBroker campaignBroker;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @Before
    public void setUp(){
        userRepository.deleteAllInBatch();
        String userNumber = "0605550000";
        testUser = new User(userNumber, "test user");
        userRepository.save(testUser);
    }


    @Test
    public void testCreateAndUpdateCampaign(){
       Campaign campaign = campaignBroker.createCampaign("national campaign","234","our national campaign",testUser.getUid(), Instant.now(), java.time.Instant.MAX, null);
        Assert.assertNotNull(campaign);
        Assert.assertNotNull(campaign.getCreatedByUser().getPhoneNumber(), "0605550000");
        Assert.assertNotNull(campaign.getCampaignCode(), "234");
        Assert.assertNotNull(campaign.getCampaignName(), "national campaign");

        Campaign updatedCampaign = campaignBroker.addCampaignMessage("234","Test message", Locale.ENGLISH, MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, testUser, null);
        Assert.assertNotNull(updatedCampaign);
        Assert.assertEquals(updatedCampaign.getCampaignName(), "national campaign");
        Assert.assertEquals(updatedCampaign.getCampaignMessages().size(), 1);
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getMessage(), "Test message");
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getChannel(), UserInterfaceType.USSD);
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getLocale(), Locale.ENGLISH);

        Campaign reUpdatedCampaign = campaignBroker.addActionsToCampaignMessage("234", updatedCampaign.getCampaignMessages().iterator().next().getUid(), Arrays.asList(CampaignActionType.JOIN_MASTER_GROUP), testUser);
        Assert.assertNotNull(reUpdatedCampaign);
        Assert.assertNotNull(reUpdatedCampaign.getCampaignMessages().iterator().next().getCampaignMessageActionSet());
        Assert.assertEquals(reUpdatedCampaign.getCampaignMessages().iterator().next().getCampaignMessageActionSet().size(), 1);
        Assert.assertEquals(reUpdatedCampaign.getCampaignMessages().iterator().next().getCampaignMessageActionSet().iterator().next().getActionType(), CampaignActionType.JOIN_MASTER_GROUP);

        Campaign campaignByName = campaignBroker.getCampaignDetailsByName("national campaign");
        Assert.assertNotNull(campaignByName);
        Assert.assertEquals(campaignByName.getCampaignName(), "national campaign");

        Set<CampaignMessage> campaignMessageSet = campaignBroker.getCampaignMessagesByCampaignName("national campaign",MessageVariationAssignment.CONTROL,UserInterfaceType.USSD, Locale.ENGLISH);
        Assert.assertNotNull(campaignMessageSet);

        Set<CampaignMessage> campaignMessageSet0 = campaignBroker.getCampaignMessagesByCampaignNameAndLocale("national campaign",MessageVariationAssignment.CONTROL, Locale.ENGLISH, UserInterfaceType.USSD);
        Assert.assertNotNull(campaignMessageSet0);
        Assert.assertEquals(campaignMessageSet0.size(), 1);

        Set<CampaignMessage> campaignMessageSet1 = campaignBroker.getCampaignMessagesByCampaignCodeAndLocale("234",MessageVariationAssignment.CONTROL, Locale.ENGLISH,UserInterfaceType.USSD);
        Assert.assertNotNull(campaignMessageSet1);
        Assert.assertEquals(campaignMessageSet1.size(), 1);
    }

}
