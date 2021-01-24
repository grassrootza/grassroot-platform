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
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.campaign.CampaignType;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Autowired
    private GroupRepository groupRepository;

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

    @Test
    public void testFindCampaignsForUser_ShouldReturnAllCampaignsCreatedByUser() {
        User creatingUser = userRepository.save(new User("33425", null, null));
        User nonCreatingUser = userRepository.save(new User("33426", null, null));
        Account account = accountRepository.save(new Account(creatingUser, "test", AccountType.ENTERPRISE, creatingUser));
        Campaign campaign1 = new Campaign("campaign 1", "001", "campaign one", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        Campaign campaign2 = new Campaign("campaign 2", "002", "campaign two", nonCreatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        Campaign campaign3 = new Campaign("campaign 3", "003", "campaign three", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        campaignRepository.saveAll(Arrays.asList(new Campaign[]{campaign1, campaign2, campaign3}));
        List<Campaign> campaignsManagedByUser = campaignRepository.findCampaignsManagedByUser(creatingUser.getId());
        Assert.assertEquals(2, campaignsManagedByUser.size());
    }

    @Test
    public void testFindCampaignsForUser_ShouldReturnAllCampaignsCreatedByUserAcrossMultipleAccounts() {
        User creatingUser = userRepository.save(new User("33425", null, null));
        User nonCreatingUser = userRepository.save(new User("33426", null, null));
        User accountOwningUser1 = userRepository.save(new User("33427", null, null));
        User accountOwningUser2 = userRepository.save(new User("33428", null, null));
        User accountOwningUser3 = userRepository.save(new User("33429", null, null));
        Account account1 = accountRepository.save(new Account(accountOwningUser1, "test", AccountType.ENTERPRISE, accountOwningUser1));
        Account account2 = accountRepository.save(new Account(accountOwningUser2, "test", AccountType.ENTERPRISE, accountOwningUser1));
        Account account3 = accountRepository.save(new Account(accountOwningUser3, "test", AccountType.ENTERPRISE, accountOwningUser1));
        Account account4 = accountRepository.save(new Account(accountOwningUser3, "test", AccountType.ENTERPRISE, accountOwningUser1));
        Campaign campaign1 = new Campaign("campaign 1", "001", "campaign one", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account1);
        Campaign campaign2 = new Campaign("campaign 2", "002", "campaign two", nonCreatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account2);
        Campaign campaign3 = new Campaign("campaign 3", "003", "campaign three", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account3);
        Campaign campaign4 = new Campaign("campaign 4", "004", "campaign four", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account4);
        Campaign campaign5 = new Campaign("campaign 5", "005", "campaign five", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account4);
        campaignRepository.saveAll(Arrays.asList(new Campaign[]{campaign1, campaign2, campaign3, campaign4, campaign5}));
        List<Campaign> campaignsManagedByUser = campaignRepository.findCampaignsManagedByUser(creatingUser.getId());
        Assert.assertEquals(4, campaignsManagedByUser.size());
    }

    @Test
    public void testFindCampaignsForUser_ShouldReturnAllCampaignsWhereUserIsAnOrganisingMember() {
        User creatingUser = userRepository.save(new User("33425", null, null));
        User organisingMember = userRepository.save(new User("33426", null, null));
        Account account = accountRepository.save(new Account(creatingUser, "test", AccountType.ENTERPRISE, creatingUser));
        Group groupOrganisedByUser1 = new Group("test", GroupPermissionTemplate.DEFAULT_GROUP, creatingUser);
        groupOrganisedByUser1.addMember(organisingMember, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_SYS_ADMIN, null);
        Group groupOrganisedByUser2 = new Group("test", GroupPermissionTemplate.DEFAULT_GROUP, creatingUser);
        groupOrganisedByUser2.addMember(organisingMember, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_SYS_ADMIN, null);
        groupRepository.save(groupOrganisedByUser1);
        groupRepository.save(groupOrganisedByUser2);
        Campaign campaign1 = new Campaign("campaign 1", "001", "campaign one", creatingUser, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS),CampaignType.PETITION, "", account);
        campaign1.setMasterGroup(groupOrganisedByUser1);
        Campaign campaign2 = new Campaign("campaign 2", "002", "campaign two", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        campaign2.setMasterGroup(groupOrganisedByUser2);
        Campaign campaign3 = new Campaign("campaign 3", "003", "campaign three", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        campaignRepository.saveAll(Arrays.asList(new Campaign[]{campaign1, campaign2, campaign3}));

        List<Campaign> campaignsManagedByUser = campaignRepository.findCampaignsManagedByUser(organisingMember.getId());
        Assert.assertEquals(2, campaignsManagedByUser.size());
    }

    @Test
    public void testFindCampaignsForUser_ShouldReturnCampaignsSortedByCreationDateAscending() {
        User creatingUser = userRepository.save(new User("33425", null, null));
        User organisingMember = userRepository.save(new User("33426", null, null));
        Account account = accountRepository.save(new Account(creatingUser, "test", AccountType.ENTERPRISE, creatingUser));
        Group groupOrganisedByUser1 = new Group("test", GroupPermissionTemplate.DEFAULT_GROUP, creatingUser);
        groupOrganisedByUser1.addMember(organisingMember, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_SYS_ADMIN, null);
        groupRepository.save(groupOrganisedByUser1);
        Campaign campaign1 = new Campaign("campaign 1", "001", "campaign one", creatingUser, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS),CampaignType.PETITION, "", account);
        campaign1.setCreatedDateTime(Instant.now());
        Campaign campaign2 = new Campaign("campaign 2", "002", "campaign two", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        campaign2.setCreatedDateTime(Instant.now().plus(2, ChronoUnit.DAYS));
        Campaign campaign3 = new Campaign("campaign 3", "003", "campaign three", creatingUser, Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), CampaignType.PETITION,"",account);
        campaign3.setCreatedDateTime(Instant.now().minus(1, ChronoUnit.DAYS));
        campaignRepository.saveAll(Arrays.asList(new Campaign[]{campaign1, campaign2, campaign3}));

        List<Campaign> campaignsManagedByUser = campaignRepository.findCampaignsManagedByUser(creatingUser.getId());
        Assert.assertEquals("003", campaignsManagedByUser.get(0).getCampaignCode());
        Assert.assertEquals("001", campaignsManagedByUser.get(1).getCampaignCode());
        Assert.assertEquals("002", campaignsManagedByUser.get(2).getCampaignCode());
    }
}
