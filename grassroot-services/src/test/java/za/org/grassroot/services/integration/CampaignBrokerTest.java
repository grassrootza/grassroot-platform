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
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.campaign.CampaignBroker;

import java.time.Instant;
import java.util.Locale;

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

    @Autowired
    private GroupRepository groupRepository;

    private User testUser;
    private Group testGroup;

    @Before
    public void setUp(){
        userRepository.deleteAllInBatch();
        String userNumber = "0605550000";
        testUser = new User(userNumber, "test user", null);
        userRepository.save(testUser);
        String groupName = "testGroup";
        testGroup = groupRepository.save(new Group(groupName, testUser));
    }


    @Test
    public void testCreateAndUpdateCampaign(){
        Campaign campaign = campaignBroker.create("national campaign","234","our national campaign",testUser.getUid(),
                testGroup.getUid(), Instant.now(), java.time.Instant.MAX, null, CampaignType.INFORMATION,null);
        Assert.assertNotNull(campaign);
        Assert.assertNotNull(campaign.getCreatedByUser().getPhoneNumber(), "0605550000");
        Assert.assertNotNull(campaign.getCampaignCode(), "234");
        Assert.assertNotNull(campaign.getName(), "national campaign");

        Campaign updatedCampaign = campaignBroker
                .addCampaignMessage(campaign.getUid(),"Test message", Locale.ENGLISH, MessageVariationAssignment.CONTROL, UserInterfaceType.USSD, testUser, null);
        Assert.assertNotNull(updatedCampaign);
        Assert.assertEquals(updatedCampaign.getName(), "national campaign");
        Assert.assertEquals(updatedCampaign.getCampaignMessages().size(), 1);
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getMessage(), "Test message");
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getChannel(), UserInterfaceType.USSD);
        Assert.assertEquals(updatedCampaign.getCampaignMessages().iterator().next().getLocale(), Locale.ENGLISH);

        Campaign reUpdatedCampaign = campaignBroker.addActionToCampaignMessage(campaign.getUid(), updatedCampaign.getCampaignMessages().iterator().next().getUid(), CampaignActionType.JOIN_MASTER_GROUP,"test action message",Locale.ENGLISH,MessageVariationAssignment.CONTROL,UserInterfaceType.USSD, testUser,null);
        Assert.assertNotNull(reUpdatedCampaign);
        Assert.assertNotNull(reUpdatedCampaign.getCampaignMessages().iterator().next().getCampaignMessageActionSet());
        Assert.assertEquals(reUpdatedCampaign.getCampaignMessages().iterator().next().getCampaignMessageActionSet().size(), 1);
        Assert.assertEquals(reUpdatedCampaign.getCampaignMessages().iterator().next().getCampaignMessageActionSet().iterator().next().getActionType(), CampaignActionType.JOIN_MASTER_GROUP);

        Campaign linkedCampaign = campaignBroker.updateMasterGroup("234", testGroup.getUid(),testUser.getUid());
        Assert.assertNotNull(linkedCampaign);
        Assert.assertNotNull(linkedCampaign.getMasterGroup());
        Assert.assertEquals(linkedCampaign.getMasterGroup().getId(),testGroup.getId());

        // todo: fix when this part of campaigns is also fixed up
//        Campaign cam = campaignBroker.addUserToCampaignMasterGroup(reUpdatedCampaign.getUid(),"0605550000");
//        Assert.assertNotNull(cam);
//        Assert.assertNotNull(cam.getMasterGroup());
//        Assert.assertNotNull(cam.getMasterGroup().getMembers());
//        Assert.assertEquals(cam.getMasterGroup().getMembers().size(), 1);
//        Assert.assertEquals(cam.getMasterGroup().getMembers().iterator().next().getPhoneNumber(),"27605550000");
    }

}
