package za.org.grassroot.core.repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Created by luke on 2015/11/14.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class AccountRepositoryTest {

    private static final Logger log = LoggerFactory.getLogger(AccountRepositoryTest.class);

    private static final String accountName = "Paying institution";
    private static final String billingEmail = "accounts@institution.com";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    private User testUser;

    @Before
    public void setUp() throws Exception {
        testUser = userRepository.save(new User("0601112345", null, null));
    }

    @Test
    @Rollback
    public void shouldSaveAndReturnId() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, "accountname", AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        account = accountRepository.save(account);
        assertNotEquals(null, account.getId());

        // assertEquals(Long.parseLong("2"),Long.parseLong(account.getId().toString())); // looks like not clearing cache, hence commenting out
    }

    @Test
    public void shouldCreateAndSaveAccount() {
        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        accountRepository.save(account);

        assertThat(accountRepository.count(), is(1L));

        Account accountFromDb = accountRepository.findAll().iterator().next();

        assertNotNull(accountFromDb.getId());
        assertNotNull(accountFromDb.getCreatedDateTime());

        assertThat(accountFromDb.getAccountName(), is(accountName));
        assertFalse(accountFromDb.isEnabled()); // since need to enable after created
    }

    @Test
    public void shouldHandleAccountAdmins() {
        assertThat(accountRepository.count(), is (0L));
        User testUser2 = userRepository.save(new User("0701112345", null, null));

        Account account = accountRepository.save(new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY));
        testUser.setPrimaryAccount(account);
        testUser = userRepository.save(testUser);

        assertNotNull(account.getId());
        assertTrue(testUser.getAccountsAdministered().contains(account));
        assertTrue(account.getAdministrators().contains(testUser));
        assertFalse(testUser2.getAccountsAdministered().contains(account));
        assertFalse(account.getAdministrators().contains(testUser2));

        Account account2 = accountRepository.save(new Account(testUser2, accountName + "2", AccountType.HEAVY, testUser2, null, AccountBillingCycle.ANNUAL));
        testUser2.setPrimaryAccount(account2);
        testUser2 = userRepository.save(testUser2);

        account2.addAdministrator(testUser);
        account2 = accountRepository.save(account2);
        testUser.addAccountAdministered(account2);
        testUser = userRepository.save(testUser);

        assertNotNull(account2.getId());
        assertTrue(testUser2.getAccountsAdministered().contains(account2));
        assertTrue(account2.getAdministrators().contains(testUser2));
        assertTrue(testUser.getAccountsAdministered().contains(account2));
        assertTrue(account2.getAdministrators().contains(testUser));

        account2.removeAdministrator(testUser);
        testUser.removeAccountAdministered(account2);
        account2 = accountRepository.save(account2);
        testUser = userRepository.save(testUser);

        assertFalse(testUser.getAccountsAdministered().contains(account2));
        assertFalse(account2.getAdministrators().contains(testUser));
    }

    @Test
    public void shouldSetBillingAddress() {
        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        accountRepository.save(account);

        assertThat(accountRepository.count(), is(1L));

        Account accountFromDb = accountRepository.findAll().iterator().next();

        // todo : test with a different user
        testUser.setEmailAddress(billingEmail);
        account.setBillingUser(testUser);
        accountFromDb = accountRepository.save(accountFromDb);

        assertNotNull(accountFromDb.getId());
        assertNotNull(accountFromDb.getCreatedDateTime());
        assertThat(accountFromDb.getAccountName(), is(accountName));
        assertThat(accountFromDb.getBillingUser().getEmailAddress(), is(billingEmail));

    }

    @Test
    @Rollback
    public void shouldFindByAccountName() {
        assertThat(accountRepository.count(), is(0L));
        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        account = accountRepository.save(account);
        List<Account> accountList = accountRepository.findByAccountName(accountName);
        assertEquals(accountList.size(), 1);
        Account accountFromDb = accountList.get(0);
        assertNotNull(accountFromDb);
        assertEquals(accountName, accountFromDb.getAccountName());
    }

    @Test
    @Rollback
    public void shouldFindByBillingUser() {

        assertThat(accountRepository.count(), is(0L));
        User billingUser = userRepository.save(new User("0601110000", "Paying the bill", null));
        billingUser.setEmailAddress(billingEmail);
        Account account = new Account(testUser, accountName, AccountType.STANDARD, billingUser, null, AccountBillingCycle.MONTHLY);
        accountRepository.save(account);
        List<Account> accountList = accountRepository.findByAccountName(accountName);
        assertEquals(accountList.size(), 1);
        Account accountFromDb = accountList.get(0);
        assertNotNull(accountFromDb);
        assertEquals(accountName, accountFromDb.getAccountName());
        assertEquals(billingEmail, accountFromDb.getBillingUser().getEmailAddress());

    }

    @Test
    @Rollback
    public void shouldDisable() {
        assertThat(accountRepository.count(), is(0L));
        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        accountRepository.save(account);
        Account accountFromDb = accountRepository.findByAccountName(accountName).get(0);
        accountFromDb.setDisabledDateTime(Instant.now());
        accountRepository.save(accountFromDb);
        Account disabledAccountFromDb = accountRepository.findByAccountName(accountName).get(0);
        assertFalse(disabledAccountFromDb.isEnabled());

    }

    @Test
    @Rollback
    public void shouldFindByEnabledAndDisabled() {

        assertThat(accountRepository.count(), is(0L));

        Account accountEnabled = new Account(testUser, accountName + "_enabled", AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        Account accountDisabled = new Account(testUser, accountName + "_disabled", AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        accountDisabled.setDisabledDateTime(Instant.now());

        accountRepository.save(accountEnabled);
        accountRepository.save(accountDisabled);

        assertThat(accountRepository.count(), is (2L));

        List<Account> enabledAccounts = accountRepository.findByDisabledDateTimeAfter(Instant.now().plus(5, ChronoUnit.MINUTES));
        assertThat(enabledAccounts.size(), is(1));
        Account enabledAccountFromDb = enabledAccounts.get(0);
        assertNotNull(enabledAccountFromDb);
        assertThat(enabledAccountFromDb.getAccountName(), is(accountName + "_enabled"));
        assertTrue(enabledAccountFromDb.getDisabledDateTime().isAfter(Instant.now()));

        List<Account> disabledAccounts = accountRepository.findByDisabledDateTimeAfter(Instant.now().minus(5, ChronoUnit.MINUTES));
        assertThat(disabledAccounts.size(), is(2));
        Optional<Account> disabledAccountFromDb = disabledAccounts.stream()
                .filter(a -> a.getDisabledDateTime().isBefore(Instant.now())).findFirst();
        assertTrue(disabledAccountFromDb.isPresent());
        assertThat(disabledAccountFromDb.get().getAccountName(), is(accountName + "_disabled"));
    }

    @Test
    @Rollback
    public void shouldSaveAndFindAdministrator() {

        assertThat(accountRepository.count(), is(0L));
        User testAdmin = new User("0505550000", null, null);
        testAdmin = userRepository.save(testAdmin);

        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        account.addAdministrator(testAdmin);
        accountRepository.save(account);

        testAdmin.setPrimaryAccount(account);
        userRepository.save(testAdmin);

        Account accountFromDbByName = accountRepository.findByAccountName(accountName).get(0);
        assertNotNull(accountFromDbByName);
        assertThat(accountFromDbByName.getAdministrators().size(), is(2));
        User adminFromAccount = accountFromDbByName.getAdministrators().iterator().next();
        assertNotNull(adminFromAccount);
        List<String> phoneNumbers = accountFromDbByName.getAdministrators().stream().map(User::getPhoneNumber).collect(Collectors.toList());
        assertTrue(phoneNumbers.contains("0505550000"));

        Account accountFromDbByAdmin = accountRepository.findByAdministrators(testAdmin);
        assertNotNull(accountFromDbByAdmin);
    }

    @Test
    @Rollback
    public void shouldSaveAndFindByPaidGroup() {

        assertThat(accountRepository.count(), is(0L));

        User testUser = new User("0505550000", null, null);
        testUser = userRepository.save(testUser);
        Group testGroup = new Group("testGroup", testUser);
        testGroup = groupRepository.save(testGroup);
        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
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
    public void shouldSaveGroupLimits() {

        assertThat(accountRepository.count(), is(0L));

        Account account = new Account(testUser, accountName, AccountType.STANDARD, testUser, null, AccountBillingCycle.MONTHLY);
        account.setMaxSizePerGroup(500);
        account.setMaxSubGroupDepth(3);
        account.setFreeFormMessages(100);
        account.setMaxNumberGroups(10);

        accountRepository.save(account);

        Account accountFromDb = accountRepository.findByAccountName(accountName).get(0);

        assertEquals(accountFromDb.getFreeFormMessages(), 100);
        assertEquals(accountFromDb.getMaxSizePerGroup(), 500);
        assertEquals(accountFromDb.getMaxSubGroupDepth(), 3);
        assertEquals(accountFromDb.getMaxNumberGroups(), 10);
        assertEquals(accountFromDb.getMaxSubGroupDepth(), 3);

        accountFromDb.setFreeFormMessages(0);
        accountFromDb = accountRepository.save(accountFromDb);
        assertEquals(accountFromDb.getFreeFormMessages(), 0);

    }

}
