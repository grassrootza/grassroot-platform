package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.ServicesTestConfig;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignMessageDTO;
import za.org.grassroot.services.campaign.MessageLanguagePair;

import java.time.Instant;
import java.util.Collections;
import java.util.Locale;

@RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = ServicesTestConfig.class)
@WithMockUser(username = "0605550000", roles={"SYSTEM_ADMIN"})
public class CampaignBrokerTest {

    @Autowired
    private CampaignBroker campaignBroker;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupRepository groupRepository;

    private User testUser;
    private Group testGroup;
    private Account testAccount;

    @Before
    public void setUp(){
        userRepository.deleteAllInBatch();
        String userNumber = "0605550000";
        testUser = new User(userNumber, "test user", null);
        userRepository.save(testUser);
        String groupName = "testGroup";
        testGroup = groupRepository.save(new Group(groupName, testUser));
        testAccount = accountRepository.save(new Account(testUser, "test", AccountType.ENTERPRISE, testUser));
        testUser.setPrimaryAccount(testAccount);
        userRepository.save(testUser);
    }


    @Test
    public void testCreateAndUpdateCampaign(){
        Campaign campaign = campaignBroker.create("national campaign", "234", "our national campaign", testUser.getUid(),
                testGroup.getUid(), Instant.now(), DateTimeUtil.getVeryLongAwayInstant(), null, CampaignType.INFORMATION,null, false, 0, null);
        Assert.assertNotNull(campaign);
        Assert.assertNotNull(campaign.getCreatedByUser().getPhoneNumber(), "0605550000");
        Assert.assertNotNull(campaign.getCampaignCode(), "234");
        Assert.assertNotNull(campaign.getName(), "national campaign");

        CampaignMessageDTO messageDTO = new CampaignMessageDTO();
        messageDTO.setChannel(UserInterfaceType.USSD);
        messageDTO.setMessages(Collections.singletonList(new MessageLanguagePair(Locale.ENGLISH, "Test message")));
        messageDTO.setLinkedActionType(CampaignActionType.OPENING);
        messageDTO.setMessageId("test-message-ID");

        // "Test message", Locale.ENGLISH, MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, testUser, null
        Campaign updatedCampaign = campaignBroker
                .setCampaignMessages(testUser.getUid(), campaign.getUid(), Collections.singleton(messageDTO));
        Assert.assertNotNull(updatedCampaign);
        Assert.assertEquals(updatedCampaign.getName(), "national campaign");
        Assert.assertEquals(updatedCampaign.getCampaignMessages().size(), 1);
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getMessage(), "Test message");
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getChannel(), UserInterfaceType.USSD);
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getLocale(), Locale.ENGLISH);

        Campaign linkedCampaign = campaignBroker.updateMasterGroup(campaign.getUid(), testGroup.getUid(),testUser.getUid());
        Assert.assertNotNull(linkedCampaign);
        Assert.assertNotNull(linkedCampaign.getMasterGroup());
        Assert.assertEquals(linkedCampaign.getMasterGroup().getId(),testGroup.getId());
    }

}
