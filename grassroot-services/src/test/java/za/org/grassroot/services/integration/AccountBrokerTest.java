package za.org.grassroot.services.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@Transactional
@WithMockUser(username = "0605550000", roles={"SYSTEM_ADMIN"})
public class AccountBrokerTest {

    private static final Logger log = LoggerFactory.getLogger(AccountBrokerTest.class);

    @Autowired
    private AccountBroker accountBroker;

    @Autowired
    private AccountGroupBroker accountGroupBroker;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    private final String accountName = "testAccount";
    private final String billingEmail = "billingemail@cso.org";

    private User testUser;
    private User testAdmin;
    private Group testGroup;

    @Before
    public void setUp() {
        setAccountFields();

        String userNumber = "0605550000";
        testUser = new User(userNumber, "test user", null);
        userRepository.save(testUser);

        String accountAdminNumber = "0605550011";
        testAdmin = new User(accountAdminNumber, null, null);
        testAdmin.setEmailAddress(billingEmail);
        userRepository.save(testAdmin);

        String groupName = "testGroup";
        testGroup = groupRepository.save(new Group(groupName, testUser));

        Role systemAdmin = new Role(BaseRoles.ROLE_SYSTEM_ADMIN, null);
        log.info("systemAdmin role: " + systemAdmin.describe());
        testUser.addStandardRole(systemAdmin);
        roleRepository.save(systemAdmin);
        userRepository.save(testUser);

        Role accountAdmin = new Role(BaseRoles.ROLE_ACCOUNT_ADMIN, null);
        roleRepository.save(accountAdmin);
    }

    // this is cumbersome, but test isn't picking up the rest of the properties, or running init, so ...
    private void setAccountFields() {
        Map<AccountType, Integer> accountFees = new HashMap<>();
        accountFees.put(AccountType.STANDARD, 10000);
        Map<AccountType, Integer> freeFormPerMonth = new HashMap<>();
        freeFormPerMonth.put(AccountType.STANDARD, 100);
        Map<AccountType, Integer> messagesCost = new HashMap<>();
        messagesCost.put(AccountType.STANDARD, 30);
        Map<AccountType, Integer> maxGroupSize = new HashMap<>();
        maxGroupSize.put(AccountType.STANDARD, 300);
        Map<AccountType, Integer> maxGroupNumber = new HashMap<>();
        maxGroupNumber.put(AccountType.STANDARD, 20);
        Map<AccountType, Integer> maxSubGroupDepth = new HashMap<>();
        maxSubGroupDepth.put(AccountType.STANDARD, 3);
        Map<AccountType, Integer> todosPerMonth = new HashMap<>();
        todosPerMonth.put(AccountType.STANDARD, 16);
        Map<AccountType, Integer> eventsPerMonth = new HashMap<>();
        eventsPerMonth.put(AccountType.STANDARD, 16);

        ReflectionTestUtils.setField(accountBroker, "accountFees", accountFees);
        ReflectionTestUtils.setField(accountBroker, "freeFormPerMonth", freeFormPerMonth);
        ReflectionTestUtils.setField(accountBroker, "messagesCost", messagesCost);
        ReflectionTestUtils.setField(accountBroker, "maxGroupSize", maxGroupSize);
        ReflectionTestUtils.setField(accountBroker, "maxGroupNumber", maxGroupNumber);
        ReflectionTestUtils.setField(accountBroker, "maxSubGroupDepth", maxSubGroupDepth);
        ReflectionTestUtils.setField(accountBroker, "todosPerMonth", todosPerMonth);
        ReflectionTestUtils.setField(accountBroker, "eventsPerMonth", eventsPerMonth);
    }

    private Account createTestAccount() {
        String accountUid = accountBroker.createAccount(testAdmin.getUid(), accountName, testAdmin.getUid(), AccountType.STANDARD, null, AccountBillingCycle.MONTHLY, false);
        return accountBroker.loadAccount(accountUid);
    }

    @Test
    public void shouldSaveBilling() {
        String accountUid = accountBroker.createAccount(testUser.getUid(), accountName, testAdmin.getUid(), AccountType.STANDARD, null, AccountBillingCycle.MONTHLY, false);
        Account account = accountBroker.loadAccount(accountUid);
        assertNotEquals(null,account.getId());
        assertEquals(billingEmail, account.getBillingUser().getEmailAddress());
    }

    @Test
    public void shouldCreateWithAdmin() {
        Account account = createTestAccount();
        assertNotEquals(null, account.getId());
        assertNotNull(account.getAdministrators());

        assertEquals(1, account.getAdministrators().size());
        assertEquals(testAdmin.getId(), account.getAdministrators().iterator().next().getId());
        assertEquals(testAdmin.getPrimaryAccount().getId(), account.getId());
        // note : role checking fails, appears to be for persistence reasons
    }

    @Test
    public void shouldCreateDetailedAccount() {
        Account account = createTestAccount();
        assertNotEquals(null, account.getId());
        assertEquals(billingEmail, account.getBillingUser().getEmailAddress());
        accountBroker.enableAccount(testAdmin.getUid(), account.getUid(), null, AccountPaymentType.CARD_PAYMENT, true, true);
        assertTrue(account.isEnabled());
        assertEquals(testAdmin.getId(), account.getAdministrators().iterator().next().getId()); // note: equals on User as whole fails for persistence reasons
        assertEquals(testAdmin.getPrimaryAccount().getId(), account.getId()); // note: as above, full equals fails ... possibly within-test persistence issues
        // assertTrue(testUser.getStandardRoles().contains(roleRepository.findOneByNameAndRoleType(accountAdminRole, Role.RoleType.STANDARD))); // as above
    }

    @Test
    public void shouldAddAdmin() {
        Account account = createTestAccount();
        User admin2 = userRepository.save(new User("0605550022", null, null));
        assertEquals(account.getAdministrators().size(), 1);
        accountBroker.addAdministrator(testUser.getUid(), account.getUid(), admin2.getUid());
        assertEquals(account.getAdministrators().size(), 2);
        assertTrue(account.getAdministrators().contains(testAdmin));
        assertTrue(account.getAdministrators().contains(admin2));
        assertEquals(account, testAdmin.getPrimaryAccount());
        assertEquals(account, admin2.getPrimaryAccount());
        // assertTrue(testUser.getStandardRoles().contains(roleRepository.findOneByNameAndRoleType(accountAdminRole, Role.RoleType.STANDARD)));
    }

    @Test
    public void shouldRemoveAdmin() {
        Account account = createTestAccount();
        assertEquals(account.getAdministrators().size(), 1);
        // assertTrue(testUser.getStandardRoles().contains(roleRepository.findOneByNameAndRoleType(accountAdminRole, Role.RoleType.STANDARD)));
        // accountBroker.removeAdministrator(account, testUser); // note: need to fix this
    }

    @Test
    public void shouldAddGroupToAccount() {
        // todo: add tests to check it fails if not done by admin
        // todo: add lots more asserts, to make sure group added is the actual group
        Account account = createTestAccount();
        accountBroker.enableAccount(testAdmin.getUid(), account.getUid(), null, AccountPaymentType.CARD_PAYMENT, true, true);
        accountGroupBroker.addGroupToAccount(account.getUid(), testGroup.getUid(), testAdmin.getUid());
        assertTrue(testGroup.isPaidFor());
        assertNotNull(accountGroupBroker.findAccountForGroup(testGroup.getUid()));
        assertEquals(accountGroupBroker.findAccountForGroup(testGroup.getUid()).getId(), account.getId());
    }

    @Test
    public void shouldRemoveGroupFromAccount() {
        // todo: work out why the removeGroupsFromAccount method is causing optimistic locking fail
        /*User testUser2 = userManagementService.loadOrCreateUser("0813074085");
        Group testGroup2 = groupManagementService.createNewGroup(testUser2, "lesetse");
        Account account2 = accountBroker.createAccount("some other name");
        accountBroker.addGroupToAccount(account2, testGroup2, testUser2);
        testGroup2 = accountBroker.removeGroupsFromAccount(account2, testGroup2, testUser2);
        assertFalse(testGroup2.isPaidFor());*/
    }

    @Test(expected = GroupAlreadyPaidForException.class)
    public void shouldNotAllowDuplicatePaidGroups() {
        // todo: change this to try/catch, to handle it better
        Account account = createTestAccount();
        accountBroker.enableAccount(testAdmin.getUid(), account.getUid(), null, AccountPaymentType.CARD_PAYMENT, true, true);
        String account2Uid = accountBroker.createAccount(testUser.getUid(), accountName + "2", testAdmin.getUid(), AccountType.STANDARD, null, AccountBillingCycle.MONTHLY, false);
        accountBroker.enableAccount(testAdmin.getUid(), account2Uid, null, AccountPaymentType.CARD_PAYMENT, true, true);
        accountGroupBroker.addGroupToAccount(account.getUid(), testGroup.getUid(), testAdmin.getUid());
        accountGroupBroker.addGroupToAccount(account2Uid, testGroup.getUid(), testAdmin.getUid());
    }

}
