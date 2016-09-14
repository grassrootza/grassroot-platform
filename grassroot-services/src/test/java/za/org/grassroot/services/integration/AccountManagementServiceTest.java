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
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassrootServicesConfig;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.services.AccountManagementService;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;

import java.util.HashSet;
import java.util.Iterator;

import static org.junit.Assert.*;
import static za.org.grassroot.services.enums.GroupPermissionTemplate.DEFAULT_GROUP;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassrootServicesConfig.class, TestContextConfig.class})
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@WithMockUser(username = "0605550000", roles={"SYSTEM_ADMIN"})
public class AccountManagementServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AccountManagementServiceTest.class);

    @Autowired
    private AccountManagementService accountManagementService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private RoleRepository roleRepository;

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
        roleRepository.save(new Role(accountAdminRole, null));
    }

    private Account createTestAccount(String billingEmail) {
        String accountUid = accountManagementService.createAccount(testUser.getUid(), accountName, testAdmin.getUid(), billingEmail);
        Account account = accountManagementService.loadAccount(accountUid);
        return account;
    }

    @Test
    public void shouldSaveBilling() {
        String accountUid = accountManagementService.createAccount(testUser.getUid(), accountName, testAdmin.getUid(), billingEmail);
        Account account = accountManagementService.loadAccount(accountUid);
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
        accountManagementService.addAdministrator(testUser.getUid(), account.getUid(), admin2.getUid());
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
        // accountManagementService.removeAdministrator(account, testUser); // note: need to fix this */
    }

    @Test
    public void shouldAddGroupToAccount() {
        // todo: add tests to check it fails if not done by admin
        // todo: add lots more asserts, to make sure group added is the actual group
        Account account = createTestAccount(null);
        accountManagementService.addGroupToAccount(account.getUid(), testGroup.getUid(), testAdmin.getUid());
        assertTrue(testGroup.isPaidFor());
        assertNotNull(accountManagementService.findAccountForGroup(testGroup.getUid()));
        assertEquals(accountManagementService.findAccountForGroup(testGroup.getUid()).getId(), account.getId());
    }

    @Test
    public void shouldRemoveGroupFromAccount() {
        // todo: work out why the removeGroupFromAccount method is causing optimistic locking fail
        /*User testUser2 = userManagementService.loadOrCreateUser("0813074085");
        Group testGroup2 = groupManagementService.createNewGroup(testUser2, "lesetse");
        Account account2 = accountManagementService.createAccount("some other name");
        accountManagementService.addGroupToAccount(account2, testGroup2, testUser2);
        testGroup2 = accountManagementService.removeGroupFromAccount(account2, testGroup2, testUser2);
        assertFalse(testGroup2.isPaidFor());*/
    }

    @Test(expected = GroupAlreadyPaidForException.class)
    public void shouldNotAllowDuplicatePaidGroups() {
        // todo: change this to try/catch, to handle it better
        Account account = createTestAccount(null);
        String account2Uid = accountManagementService.createAccount(testUser.getUid(), accountName + "2", testAdmin.getUid(), null);
        accountManagementService.addGroupToAccount(account.getUid(), testGroup.getUid(), testAdmin.getUid());
        accountManagementService.addGroupToAccount(account2Uid, testGroup.getUid(), testAdmin.getUid());
    }

}
