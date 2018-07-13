package za.org.grassroot.core.repository;


import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.util.*;

@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
public class CampaignRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Test
    public void testCreateCampaign(){
        User user = userRepository.save(new User("3456", null, null));
        Account account = accountRepository.save(new Account(user, "test", AccountType.ENTERPRISE, user));
        Campaign campaign = campaignRepository.saveAndFlush(new Campaign("Test","234","Durban campaign",user, Instant.now(), Instant.now(), CampaignType.ACQUISITION,null, account));
        Assert.assertNotNull(campaign);
        Assert.assertNotNull(campaign.getUid());
        Assert.assertNotNull(campaign.getCreatedDateTime());
        Assert.assertEquals(campaign.getName(),"Test");
        Assert.assertEquals(campaign.getCampaignCode(),"234");
        Assert.assertNotNull(campaign.getCreatedByUser());
        Assert.assertEquals(campaign.getCreatedByUser().getPhoneNumber(),"3456");
        Assert.assertEquals(campaign.getCampaignType(), CampaignType.ACQUISITION);
    }

    @Test
    public void testCampaignMessages(){
        User user = userRepository.save(new User("3456", null, null));
        Account account = accountRepository.save(new Account(user, "test", AccountType.ENTERPRISE, user));
        Campaign campaign =  new Campaign("Test","234","Durban campaign",user, Instant.now(), Instant.now(), CampaignType.ACQUISITION, null, account);
        Set<CampaignMessage> messageSet = new HashSet<>();
        CampaignMessage campaignMessage = new CampaignMessage(user, campaign, CampaignActionType.OPENING, "testing_123", Locale.forLanguageTag("en-US"), "Please join Campaign", UserInterfaceType.USSD, MessageVariationAssignment.CONTROL);
        messageSet.add(campaignMessage);
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
        User user = userRepository.save(new User("3456", null, null));
        Account account = accountRepository.save(new Account(user, "test", AccountType.ENTERPRISE, user));
        Campaign campaign =  new Campaign("Test","234","Durban campaign",user, Instant.now(), Instant.MAX, CampaignType.INFORMATION, null, account);
        Set<CampaignMessage> messageSet = new HashSet<>();
        CampaignMessage campaignMessage = new CampaignMessage(user, campaign, CampaignActionType.OPENING, "testing_123", Locale.forLanguageTag("en-US"), "Please join Campaign", UserInterfaceType.USSD, MessageVariationAssignment.CONTROL);
        messageSet.add(campaignMessage);
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
