package za.org.grassroot.core.repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by luke on 2015/11/14.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class AccountRepositoryTest {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(AccountRepositoryTest.class);

    private static final String accountName = "Paying institution";
    private static final String billingEmail = "accounts@institution.com";

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    PaidGroupRepository paidGroupRepository;

    User testUser;

    @Before
    public void setUp() throws Exception {
        testUser = userRepository.save(new User("0601112345"));
    }

    @Test
    @Rollback
    public void shouldSaveAndReturnId() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, "accountname");
        account = accountRepository.save(account);
        assertNotEquals(null, account.getId());

        // assertEquals(Long.parseLong("2"),Long.parseLong(account.getId().toString())); // looks like not clearing cache, hence commenting out
    }

    @Test
    public void shouldCreateAndSaveAccount() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, accountName);
        accountRepository.save(account);

        assertThat(accountRepository.count(), is(1L));

        Account accountFromDb = accountRepository.findAll().iterator().next();

        assertNotNull(accountFromDb.getId());
        assertNotNull(accountFromDb.getCreatedDateTime());

        assertThat(accountFromDb.getAccountName(), is(accountName));
        assertTrue(accountFromDb.isEnabled());
    }

    @Test
    public void shouldSetBillingAddress() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, accountName);
        accountRepository.save(account);

        assertThat(accountRepository.count(), is(1L));

        Account accountFromDb = accountRepository.findAll().iterator().next();
        accountFromDb.setPrimaryEmail(billingEmail);
        accountFromDb = accountRepository.save(accountFromDb);

        assertNotNull(accountFromDb.getId());
        assertNotNull(accountFromDb.getCreatedDateTime());
        assertThat(accountFromDb.getAccountName(), is(accountName));
        assertThat(accountFromDb.getPrimaryEmail(), is(billingEmail));

    }

    @Test
    @Rollback
    public void shouldFindByAccountName() {

        assertThat(accountRepository.count(), is(0L));
        Account account = new Account(testUser, accountName);
        account = accountRepository.save(account);
        List<Account> accountList = accountRepository.findByAccountName(accountName);
        assertEquals(accountList.size(), 1);
        Account accountFromDb = accountList.get(0);
        assertNotNull(accountFromDb);
        assertEquals(accountName, accountFromDb.getAccountName());

    }

    @Test
    @Rollback
    public void shouldFindByBillingMail() {

        assertThat(accountRepository.count(), is(0L));
        Account account = new Account(testUser, accountName);
        account.setPrimaryEmail(billingEmail);
        accountRepository.save(account);
        List<Account> accountList = accountRepository.findByAccountName(accountName);
        assertEquals(accountList.size(), 1);
        Account accountFromDb = accountList.get(0);
        assertNotNull(accountFromDb);
        assertEquals(accountName, accountFromDb.getAccountName());
        assertEquals(billingEmail, accountFromDb.getPrimaryEmail());

    }

    @Test
    @Rollback
    public void shouldDisable() {

        assertThat(accountRepository.count(), is(0L));
        Account account = new Account(testUser, accountName);
        accountRepository.save(account);
        Account accountFromDb = accountRepository.findByAccountName(accountName).get(0);
        accountFromDb.setEnabled(false);
        accountRepository.save(accountFromDb);
        Account disabledAccountFromDb = accountRepository.findByAccountName(accountName).get(0);
        assertFalse(disabledAccountFromDb.isEnabled());

    }

    @Test
    @Rollback
    public void shouldFindByEnabledAndDisabled() {

        assertThat(accountRepository.count(), is(0L));
        Account accountEnabled = new Account(testUser, accountName);
        Account accountDisabled = new Account(testUser, accountName + "_disabled");
        accountDisabled.setEnabled(false);
        accountRepository.save(accountEnabled);
        accountRepository.save(accountDisabled);

        List<Account> enabledAccounts = accountRepository.findByEnabled(true);
        assertThat(enabledAccounts.size(), is(1));
        Account enabledAccountFromDb = enabledAccounts.get(0);
        assertNotNull(enabledAccountFromDb);
        assertThat(enabledAccountFromDb.getAccountName(), is(accountName));
        assertTrue(enabledAccountFromDb.isEnabled());

        List<Account> disabledAccounts = accountRepository.findByEnabled(false);
        assertThat(disabledAccounts.size(), is(1));
        Account disabledAccountFromDb = disabledAccounts.get(0);
        assertNotNull(disabledAccountFromDb);
        assertThat(disabledAccountFromDb.getAccountName(), is(accountName + "_disabled"));
        assertFalse(disabledAccountFromDb.isEnabled());

    }

    @Test
    @Rollback
    public void shouldSaveAndFindAdministrator() {

        assertThat(accountRepository.count(), is(0L));
        User testAdmin = new User("0505550000");
        testAdmin = userRepository.save(testAdmin);

        Account account = new Account(testUser, accountName);
        account.addAdministrator(testAdmin);
        accountRepository.save(account);

        testAdmin.setAccountAdministered(account);
        userRepository.save(testAdmin);

        Account accountFromDbByName = accountRepository.findByAccountName(accountName).get(0);
        assertNotNull(accountFromDbByName);
        assertThat(accountFromDbByName.getAdministrators().size(), is(1));
        User adminFromAccount = accountFromDbByName.getAdministrators().iterator().next();
        assertNotNull(adminFromAccount);
        assertThat(adminFromAccount.getPhoneNumber(), is("0505550000"));

        Account accountFromDbByAdmin = accountRepository.findByAdministrators(testAdmin);
        assertNotNull(accountFromDbByAdmin);
    }

    @Test
    @Rollback
    public void shouldSaveAndFindByPaidGroup() {

        assertThat(accountRepository.count(), is(0L));

        User testUser = new User("0505550000");
        testUser = userRepository.save(testUser);
        Group testGroup = new Group("testGroup", testUser);
        testGroup = groupRepository.save(testGroup);
        Account account = new Account(testUser, accountName);
        account = accountRepository.save(account);
        PaidGroup testPaidGroup = new PaidGroup(testGroup, account, testUser);
        testPaidGroup = paidGroupRepository.save(testPaidGroup);

        account.addPaidGroup(testPaidGroup);
        accountRepository.save(account);

        Account accountFromDbByName = accountRepository.findByAccountName(accountName).get(0);
        assertNotNull(accountFromDbByName);
        assertNotNull(accountFromDbByName.getPaidGroups());
        assertThat(accountFromDbByName.getPaidGroups().size(), is(1));
        PaidGroup paidGroupFromAccount = accountFromDbByName.getPaidGroups().iterator().next();
        assertNotNull(paidGroupFromAccount);
        assertThat(paidGroupFromAccount.getAccount(), is(accountFromDbByName));
        assertThat(paidGroupFromAccount.getGroup(), is(testGroup));
        assertThat(paidGroupFromAccount.getAddedByUser(), is(testUser));

        Account accountFromDbByPaidGroup = accountRepository.findByPaidGroups(testPaidGroup);
        assertNotNull(accountFromDbByPaidGroup);
        assertThat(accountFromDbByPaidGroup.getAccountName(), is(accountName));

    }

    @Test
    @Rollback
    public void shouldSaveBooleanFlags() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, accountName);
        accountRepository.save(account);

        Account accountFromDb = accountRepository.findByAccountName(accountName).get(0);

        assertTrue(accountFromDb.isRelayableMessages());
        accountFromDb.setRelayableMessages(false);
        accountFromDb = accountRepository.save(accountFromDb);
        assertFalse(accountFromDb.isRelayableMessages());

        assertTrue(accountFromDb.isFreeFormMessages());
        accountFromDb.setFreeFormMessages(false);
        accountFromDb = accountRepository.save(accountFromDb);
        assertFalse(accountFromDb.isFreeFormMessages());

    }

}
