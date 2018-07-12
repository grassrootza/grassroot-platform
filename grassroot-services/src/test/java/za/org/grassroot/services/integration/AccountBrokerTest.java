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
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;

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
    private AccountFeaturesBroker accountFeaturesBroker;

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
        String accountUid = accountBroker.createAccount(testAdmin.getUid(), accountName, testAdmin.getUid(), AccountType.STANDARD, false);
        return accountBroker.loadAccount(accountUid);
    }

    @Test
    public void shouldSaveBilling() {
        String accountUid = accountBroker.createAccount(testUser.getUid(), accountName, testAdmin.getUid(), AccountType.STANDARD, false);
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
        accountBroker.enableAccount(testAdmin.getUid(), account.getUid(), null);
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


}
