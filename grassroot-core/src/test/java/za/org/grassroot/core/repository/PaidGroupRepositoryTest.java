package za.org.grassroot.core.repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.account.PaidGroup;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountType;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


/**
 * Created by luke on 2015/11/16.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class PaidGroupRepositoryTest {

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private AccountRepository accountRepository;

    private static final String testPhoneNumber = "0810005555";
    private static final String testGroupName = "testGroup";
    private static final String testAccountName = "testAccount";

    private User testUser;
    private Group testGroup, testGroup2;
    private Account testAccount, testAccount2;

    @Before
    public void setUp() throws Exception {

        testUser = userRepository.save(new User(testPhoneNumber, null, null));
        testGroup = groupRepository.save(new Group(testGroupName, testUser));
        testGroup2 = groupRepository.save(new Group(testGroupName + "2", testUser));
        testAccount = accountRepository.save(new Account(testUser, testAccountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY));
        testAccount2 = accountRepository.save(new Account(testUser, testAccountName + "2", AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY));

    }

    @Test
    @Rollback
    public void shouldSaveAndReturnId() {

        assertThat(paidGroupRepository.count(), is(0L));

        PaidGroup paidGroup = new PaidGroup(testGroup, testAccount, testUser);
        paidGroup = paidGroupRepository.save(paidGroup);

        assertNotEquals(null, paidGroup.getId());

        // assertEquals(Long.parseLong("1"), Long.parseLong(paidGroup.getId().toString())); // DB is not rolling back so these are causing errors
    }

    @Test
    @Rollback
    public void shouldSaveWithGroupUserAccount() {

        assertThat(paidGroupRepository.count(), is(0L));

        PaidGroup paidGroup = new PaidGroup(testGroup, testAccount, testUser);
        paidGroupRepository.save(paidGroup);
        assertThat(paidGroupRepository.count(), is(1L));
        PaidGroup paidGroupFromDb = paidGroupRepository.findAll().iterator().next();
        assertNotNull(paidGroupFromDb);
        assertEquals(paidGroupFromDb.getAccount(), testAccount);
        assertEquals(paidGroupFromDb.getAddedByUser(), testUser);
        assertEquals(paidGroupFromDb.getGroup(), testGroup);

    }

    @Test
    @Rollback
    public void shouldRemoveWithGroupUserAccount() {

        assertThat(paidGroupRepository.count(), is(0L));
        PaidGroup paidGroup = new PaidGroup(testGroup, testAccount, testUser);
        paidGroupRepository.save(paidGroup);
        PaidGroup paidGroupFromDb = paidGroupRepository.findAll().iterator().next();
        assertNotNull(paidGroupFromDb);
        paidGroupFromDb.setExpireDateTime(Instant.now());
        paidGroupFromDb.setRemovedByUser(testUser);
        paidGroupRepository.save(paidGroupFromDb);
        PaidGroup paidGroupFromDb1 = paidGroupRepository.findAll().iterator().next();
        assertNotNull(paidGroupFromDb1);
        assertEquals(paidGroupFromDb.getId(), paidGroupFromDb1.getId());
        assertEquals(paidGroupFromDb1.getRemovedByUser(), testUser);
        assertTrue(paidGroupFromDb1.getExpireDateTime().isBefore(Instant.now()));
    }

    @Test
    @Rollback
    public void shouldFindPaidGroupsByExpiryDateTime() {

        assertThat(paidGroupRepository.count(), is(0L));

        PaidGroup paidGroup = paidGroupRepository.save(new PaidGroup(testGroup, testAccount, testUser));
        PaidGroup paidGroup2 = paidGroupRepository.save(new PaidGroup(testGroup, testAccount2, testUser));
        List<PaidGroup> firstFind = paidGroupRepository.findByExpireDateTimeGreaterThan(Instant.now());
        assertNotNull(firstFind);
        assertThat(firstFind.size(), is(2));
        assertTrue(firstFind.contains(paidGroup));
        assertTrue(firstFind.contains(paidGroup2));
        paidGroup2.setExpireDateTime(Instant.now());
        paidGroup2.setRemovedByUser(testUser);
        paidGroup2 = paidGroupRepository.save(paidGroup2);
        List<PaidGroup> secondFind = paidGroupRepository.findByExpireDateTimeGreaterThan(Instant.now());
        assertNotNull(secondFind);
        assertThat(secondFind.size(), is(1));
        assertTrue(secondFind.contains(paidGroup));
        assertFalse(secondFind.contains(paidGroup2));

    }

    @Test
    @Rollback
    public void shouldFindPaidGroupsByAccount() {

        assertThat(paidGroupRepository.count(), is(0L));
        PaidGroup paidGroup = paidGroupRepository.save(new PaidGroup(testGroup, testAccount, testUser));
        PaidGroup paidGroup2 = paidGroupRepository.save(new PaidGroup(testGroup2, testAccount2, testUser));
        assertThat(paidGroupRepository.count(), is(2L));
        List<PaidGroup> listAccount1 = paidGroupRepository.findByAccount(testAccount);
        List<PaidGroup> listAccount2 = paidGroupRepository.findByAccount(testAccount2);
        assertNotNull(listAccount1);
        assertNotNull(listAccount2);
        assertThat(listAccount1.size(), is(1));
        assertThat(listAccount2.size(), is(1));
        assertTrue(listAccount1.contains(paidGroup));
        assertTrue(listAccount2.contains(paidGroup2));

    }

    @Test
    @Rollback
    public void shouldFindPaidGroupByGroup() {
        assertThat(paidGroupRepository.count(), is(0L));
        PaidGroup paidGroup = paidGroupRepository.save(new PaidGroup(testGroup, testAccount, testUser));
        PaidGroup paidGroupFromDb = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(testGroup);
        assertNotNull(paidGroupFromDb);
        assertThat(paidGroupFromDb, is(paidGroup));
    }

    @Test
    @Rollback
    public void shouldFindCurrentPaidGroupByGroup() {
        // services layer needs to ensure several of these steps don't happen (test there), but testing DB behaviour here
        assertThat(paidGroupRepository.count(), is(0L));
        PaidGroup paidGroup1 = paidGroupRepository.save(new PaidGroup(testGroup, testAccount, testUser));
        PaidGroup paidGroup2 = paidGroupRepository.save(new PaidGroup(testGroup, testAccount2, testUser));
        paidGroup1.setExpireDateTime(Instant.now());
        paidGroup1.setRemovedByUser(testUser);
        paidGroupRepository.save(paidGroup1);
        PaidGroup fromReport = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(testGroup);
        assertEquals(fromReport.getId(), paidGroup2.getId()); // straight equals gives errors on hash codes
    }

}
