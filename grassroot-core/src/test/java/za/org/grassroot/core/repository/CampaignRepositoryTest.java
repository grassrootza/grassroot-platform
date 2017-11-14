package za.org.grassroot.core.repository;


import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class CampaignRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Test
    public void testCreateCampaign(){
        User user = userRepository.save(new User("3456"));
        Campaign campaign = campaignRepository.saveAndFlush(new Campaign("Test","234","Durban campaign",user, Instant.now(), Instant.now(), CampaignType.Aquisition,null));
        Assert.assertNotNull(campaign);
        Assert.assertNotNull(campaign.getUid());
        Assert.assertNotNull(campaign.getCreatedDateTime());
        Assert.assertEquals(campaign.getCampaignName(),"Test");
        Assert.assertEquals(campaign.getCampaignCode(),"234");
        Assert.assertNotNull(campaign.getCreatedByUser());
        Assert.assertEquals(campaign.getCreatedByUser().getPhoneNumber(),"3456");
        Assert.assertEquals(campaign.getCampaignType(), CampaignType.Aquisition);
    }

    @Test
    public void testCampaignMessages(){
        User user = userRepository.save(new User("3456"));
        Set<CampaignMessage> messageSet = new HashSet<>();
        CampaignMessage campaignMessage = new CampaignMessage("Please join Campaign", user, MessageVariationAssignment.CONTROL, Locale.forLanguageTag("en-US"), UserInterfaceType.USSD);
        messageSet.add(campaignMessage);
        Campaign campaign =  new Campaign("Test","234","Durban campaign",user, Instant.now(), Instant.now(), CampaignType.Aquisition, null);
        campaign.setCampaignMessages(messageSet);
        Campaign persistedCampaign = campaignRepository.saveAndFlush(campaign);
        Assert.assertNotNull(persistedCampaign);
        Assert.assertNotNull(persistedCampaign.getCampaignMessages());
        Assert.assertEquals(persistedCampaign.getCampaignMessages().size(), 1);
    }

    @Test
    @Ignore
    public void testGetCampaignByTag(){
        List<String> tags = new ArrayList<>();
        tags.add("braamfontein");
        User user = userRepository.save(new User("3456"));
        Set<CampaignMessage> messageSet = new HashSet<>();
        CampaignMessage campaignMessage = new CampaignMessage("Please join Campaign", user, MessageVariationAssignment.CONTROL,Locale.forLanguageTag("en-US"),UserInterfaceType.USSD);
        messageSet.add(campaignMessage);
        Campaign campaign =  new Campaign("Test","234","Durban campaign",user, Instant.now(), Instant.MAX, CampaignType.Information, null);
        campaign.setCampaignMessages(messageSet);
        campaign.setTags(tags);
        Campaign persistedCampaign = campaignRepository.saveAndFlush(campaign);
        Assert.assertNotNull(persistedCampaign);
        Assert.assertNotNull(persistedCampaign.getCampaignMessages());
        Assert.assertEquals(persistedCampaign.getCampaignMessages().size(), 1);
        Campaign camp = campaignRepository.findActiveCampaignByTag("braamfontein");
        Assert.assertNotNull(camp);
    }

}
