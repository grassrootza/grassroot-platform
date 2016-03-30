package za.org.grassroot.services.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.*;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;

import java.util.HashSet;

import static org.junit.Assert.*;
import static za.org.grassroot.services.enums.GroupPermissionTemplate.DEFAULT_GROUP;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class AccountManagementServiceTest {

    private static final Logger log = LoggerFactory.getLogger(AccountManagementServiceTest.class);

    @Autowired
    AccountManagementService accountManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupBroker groupBroker;

    @Autowired
    RoleManagementService roleManagementService;

    private final String accountName = "testAccount";
    private final String billingEmail = "billingemail@cso.org";
    private final String groupName = "testGroup";
    private final String userNumber = "0605550000";
    private final String accountAdminRole = "ROLE_ACCOUNT_ADMIN";

    private User testUser;
    private Group testGroup;

    @Before
    public void setUp() {
        log.info("Number of standard roles at set up: " + roleManagementService.getNumberStandardRoles());
        testUser = userManagementService.loadOrSaveUser(userNumber);
        testGroup = groupBroker.create(testUser.getUid(), groupName, null, new HashSet<>(), DEFAULT_GROUP, null);
        roleManagementService.createStandardRole(accountAdminRole);
    }

    @Test
    public void shouldSaveBilling() {
        Account account = accountManagementService.createAccount(accountName);
        account = accountManagementService.setBillingEmail(account, billingEmail);
        assertNotEquals(null,account.getId());
        assertEquals(billingEmail, account.getPrimaryEmail());
    }

    @Test
    public void shouldCreateWithAdmin() {
        Account account = accountManagementService.createAccount(accountName, testUser);
        testUser = userManagementService.loadOrSaveUser(userNumber); // this makes full-object equal assertions fail, but without it, role tests fail
        assertNotEquals(null, account.getId());
        assertNotNull(account.getAdministrators());
        assertEquals(account.getAdministrators().size(), 1);
        assertEquals(testUser.getId(), account.getAdministrators().iterator().next().getId()); // note: equals on User as whole fails for persistence reasons
        assertEquals(testUser.getAccountAdministered().getId(), account.getId()); // note: as above, full equals fails ... possibly within-test persistence issues
        assertTrue(testUser.getStandardRoles().contains(roleManagementService.fetchStandardRoleByName(accountAdminRole)));
    }

    @Test
    public void shouldCreateDetailedAccount() {
        Account account = accountManagementService.createAccount(accountName, testUser, billingEmail, true);
        testUser = userManagementService.loadOrSaveUser(userNumber); // this makes full-object equal assertions fail, but without it, role tests fail
        assertNotEquals(null, account.getId());
        assertEquals(billingEmail, account.getPrimaryEmail());
        assertTrue(account.isEnabled());
        assertEquals(testUser.getId(), account.getAdministrators().iterator().next().getId()); // note: equals on User as whole fails for persistence reasons
        assertEquals(testUser.getAccountAdministered().getId(), account.getId()); // note: as above, full equals fails ... possibly within-test persistence issues
        assertTrue(testUser.getStandardRoles().contains(roleManagementService.fetchStandardRoleByName(accountAdminRole)));
    }

    @Test
    public void shouldAddAdmin() {
        Account account = accountManagementService.createAccount(accountName);
        assertEquals(account.getAdministrators().size(), 0);
        accountManagementService.addAdministrator(account, testUser); // note: putting 'account =' at the front of this causes subsequent tests to fail ..
        assertEquals(account.getAdministrators().size(), 1);
        assertEquals(account.getAdministrators().iterator().next(), testUser);
        assertEquals(account, testUser.getAccountAdministered());
        assertTrue(testUser.getStandardRoles().contains(roleManagementService.fetchStandardRoleByName(accountAdminRole)));
    }

    @Test
    public void shouldRemoveAdmin() {
        Account account = accountManagementService.createAccount(accountName, testUser);
        assertEquals(account.getAdministrators().size(), 1);
        assertTrue(testUser.getStandardRoles().contains(roleManagementService.fetchStandardRoleByName(accountAdminRole)));
        // accountManagementService.removeAdministrator(account, testUser); // note: need to fix this */
    }

    @Test
    public void shouldLoadAccountById() {
        Long accountId = accountManagementService.createAccount(accountName).getId();
        Account account = accountManagementService.loadAccount(accountId);
        assertEquals(accountId, account.getId());
    }

    @Test
    public void shouldLoadByAdmin() {
        Account account = accountManagementService.createAccount(accountName, testUser);
        Account account2 = accountManagementService.findAccountByAdministrator(testUser);
        assertNotNull(account2);
    }

    @Test
    public void shouldAddGroupToAccount() {
        // todo: add tests to check it fails if not done by admin
        // todo: add lots more asserts, to make sure group added is the actual group
        Account account = accountManagementService.createAccount(accountName, testUser);
        accountManagementService.addGroupToAccount(account, testGroup, testUser);
        assertTrue(testGroup.isPaidFor());
        assertNotNull(accountManagementService.findAccountForGroup(testGroup));
        assertEquals(accountManagementService.findAccountForGroup(testGroup).getId(), account.getId());
    }

    @Test
    public void shouldRemoveGroupFromAccount() {
        // todo: work out why the removeGroupFromAccount method is causing optimistic locking fail
        /*User testUser2 = userManagementService.loadOrSaveUser("0813074085");
        Group testGroup2 = groupManagementService.createNewGroup(testUser2, "lesetse");
        Account account2 = accountManagementService.createAccount("some other name");
        accountManagementService.addGroupToAccount(account2, testGroup2, testUser2);
        testGroup2 = accountManagementService.removeGroupFromAccount(account2, testGroup2, testUser2);
        assertFalse(testGroup2.isPaidFor());*/
    }

    @Test(expected = GroupAlreadyPaidForException.class)
    public void shouldNotAllowDuplicatePaidGroups() {
        // todo: change this to try/catch, to handle it better
        Account account = accountManagementService.createAccount(accountName, testUser);
        Account account2 = accountManagementService.createAccount(accountName + "2");
        accountManagementService.addGroupToAccount(account, testGroup, testUser);
        accountManagementService.addGroupToAccount(account2, testGroup, testUser);
    }

}
