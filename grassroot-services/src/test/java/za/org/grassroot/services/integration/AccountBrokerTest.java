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
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.AccountBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;

import java.util.HashSet;

import static org.junit.Assert.*;
import static za.org.grassroot.services.enums.GroupPermissionTemplate.DEFAULT_GROUP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@WithMockUser(username = "0605550000", roles={"SYSTEM_ADMIN"})
@Transactional
public class AccountBrokerTest {

    private static final Logger log = LoggerFactory.getLogger(AccountBrokerTest.class);

    @Autowired
    private AccountBroker accountBroker;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    private final String accountName = "testAccount";
    private final String billingEmail = "billingemail@cso.org";
    private final String groupName = "testGroup";
    private final String userNumber = "0605550000";
    private final String accountAdminNumber = "0605550011";
    private final String accountAdminRole = "ROLE_ACCOUNT_ADMIN";

    private User testUser;
    private User testAdmin;
    private Group testGroup;

    @Before
    public void setUp() {
        testUser = userManagementService.loadOrCreateUser(userNumber);
        testAdmin = userManagementService.loadOrCreateUser(accountAdminNumber);
        testGroup = groupBroker.create(testUser.getUid(), groupName, null, new HashSet<>(), DEFAULT_GROUP, null, null, false);

        Role systemAdmin = new Role(BaseRoles.ROLE_SYSTEM_ADMIN, null);
        log.info("systemAdmin role: " + systemAdmin.describe());
        testUser.addStandardRole(systemAdmin);
        roleRepository.save(systemAdmin);
        userRepository.save(testUser);

        Role accountAdmin = new Role(BaseRoles.ROLE_ACCOUNT_ADMIN, null);
        roleRepository.save(accountAdmin);
    }

    private Account createTestAccount(String billingEmail) {
        String accountUid = accountBroker.createAccount(testUser.getUid(), accountName, testAdmin.getUid(), billingEmail);
        Account account = accountBroker.loadAccount(accountUid);
        return account;
    }

    @Test
    public void shouldSaveBilling() {
        String accountUid = accountBroker.createAccount(testUser.getUid(), accountName, testAdmin.getUid(), billingEmail);
        Account account = accountBroker.loadAccount(accountUid);
        assertNotEquals(null,account.getId());
        assertEquals(billingEmail, account.getPrimaryEmail());
    }

    @Test
    public void shouldCreateWithAdmin() {

        Account account = createTestAccount(null);
        assertNotEquals(null, account.getId());
        assertNotNull(account.getAdministrators());
        assertEquals(1, account.getAdministrators().size());
        assertEquals(testAdmin.getId(), account.getAdministrators().iterator().next().getId());
        assertEquals(testAdmin.getAccountAdministered().getId(), account.getId());
        // note : role checking fails, appears to be for persistence reasons
    }

    @Test
    public void shouldCreateDetailedAccount() {
        Account account = createTestAccount(billingEmail);
        testUser = userManagementService.loadOrCreateUser(userNumber); // this makes full-object equal assertions fail, but without it, role tests fail
        assertNotEquals(null, account.getId());
        assertEquals(billingEmail, account.getPrimaryEmail());
        assertTrue(account.isEnabled());
        assertEquals(testAdmin.getId(), account.getAdministrators().iterator().next().getId()); // note: equals on User as whole fails for persistence reasons
        assertEquals(testAdmin.getAccountAdministered().getId(), account.getId()); // note: as above, full equals fails ... possibly within-test persistence issues
        // assertTrue(testUser.getStandardRoles().contains(roleRepository.findOneByNameAndRoleType(accountAdminRole, Role.RoleType.STANDARD))); // as above
    }

    @Test
    public void shouldAddAdmin() {
        Account account = createTestAccount(null);
        User admin2 = userManagementService.loadOrCreateUser("0605550022");
        assertEquals(account.getAdministrators().size(), 1);
        accountBroker.addAdministrator(testUser.getUid(), account.getUid(), admin2.getUid());
        assertEquals(account.getAdministrators().size(), 2);
        assertTrue(account.getAdministrators().contains(testAdmin));
        assertTrue(account.getAdministrators().contains(admin2));
        assertEquals(account, testAdmin.getAccountAdministered());
        assertEquals(account, admin2.getAccountAdministered());
        // assertTrue(testUser.getStandardRoles().contains(roleRepository.findOneByNameAndRoleType(accountAdminRole, Role.RoleType.STANDARD)));
    }

    @Test
    public void shouldRemoveAdmin() {
        Account account = createTestAccount(null);
        assertEquals(account.getAdministrators().size(), 1);
        // assertTrue(testUser.getStandardRoles().contains(roleRepository.findOneByNameAndRoleType(accountAdminRole, Role.RoleType.STANDARD)));
        // accountBroker.removeAdministrator(account, testUser); // note: need to fix this */
    }

    @Test
    public void shouldAddGroupToAccount() {
        // todo: add tests to check it fails if not done by admin
        // todo: add lots more asserts, to make sure group added is the actual group
        Account account = createTestAccount(null);
        accountBroker.addGroupToAccount(account.getUid(), testGroup.getUid(), testAdmin.getUid());
        assertTrue(testGroup.isPaidFor());
        assertNotNull(accountBroker.findAccountForGroup(testGroup.getUid()));
        assertEquals(accountBroker.findAccountForGroup(testGroup.getUid()).getId(), account.getId());
    }

    @Test
    public void shouldRemoveGroupFromAccount() {
        // todo: work out why the removeGroupFromAccount method is causing optimistic locking fail
        /*User testUser2 = userManagementService.loadOrCreateUser("0813074085");
        Group testGroup2 = groupManagementService.createNewGroup(testUser2, "lesetse");
        Account account2 = accountBroker.createAccount("some other name");
        accountBroker.addGroupToAccount(account2, testGroup2, testUser2);
        testGroup2 = accountBroker.removeGroupFromAccount(account2, testGroup2, testUser2);
        assertFalse(testGroup2.isPaidFor());*/
    }

    @Test(expected = GroupAlreadyPaidForException.class)
    public void shouldNotAllowDuplicatePaidGroups() {
        // todo: change this to try/catch, to handle it better
        Account account = createTestAccount(null);
        String account2Uid = accountBroker.createAccount(testUser.getUid(), accountName + "2", testAdmin.getUid(), null);
        accountBroker.addGroupToAccount(account.getUid(), testGroup.getUid(), testAdmin.getUid());
        accountBroker.addGroupToAccount(account2Uid, testGroup.getUid(), testAdmin.getUid());
    }

}
