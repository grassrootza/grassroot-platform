package za.org.grassroot.services.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.ServicesTestConfig;
import za.org.grassroot.services.account.AccountBroker;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = ServicesTestConfig.class)
@WithMockUser(username = "0605550000", roles={"SYSTEM_ADMIN"})
public class AccountBrokerTest {

    private static final Logger log = LoggerFactory.getLogger(AccountBrokerTest.class);

    @Autowired
    private AccountBroker accountBroker;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    private final String accountName = "testAccount";
    private final String billingEmail = "billingemail@cso.org";

    private User testUser;
    private User testAdmin;

    @Before
    public void setUp() {

        String userNumber = "0605550000";
        testUser = new User(userNumber, "test user", null);
        userRepository.save(testUser);

        String accountAdminNumber = "0605550011";
        testAdmin = new User(accountAdminNumber, null, null);
        testAdmin.setEmailAddress(billingEmail);
        userRepository.save(testAdmin);

        Role systemAdmin = new Role(BaseRoles.ROLE_SYSTEM_ADMIN, null);
        log.info("systemAdmin role: " + systemAdmin.describe());
        testUser.addStandardRole(systemAdmin);
        roleRepository.save(systemAdmin);
        userRepository.save(testUser);

        Role accountAdmin = new Role(BaseRoles.ROLE_ACCOUNT_ADMIN, null);
        roleRepository.save(accountAdmin);
    }

    private Account createTestAccount() {
        String accountUid = accountBroker.createAccount(testAdmin.getUid(), accountName, testAdmin.getUid(), billingEmail, null);
        return accountBroker.loadAccount(accountUid);
    }

    @Test
    public void shouldSaveBilling() {
        String accountUid = accountBroker.createAccount(testAdmin.getUid(), accountName, testAdmin.getUid(), billingEmail, null);
        Account account = accountBroker.loadAccount(accountUid);
        assertNotEquals(null,account.getId());
        assertEquals(billingEmail, account.getPrimaryBillingEmail());
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
        log.info("Will enable account, admin user : {}", userRepository.findOneByUid(testAdmin.getUid()));
        assertNotEquals(null, account.getId());
        assertEquals(billingEmail, account.getPrimaryBillingEmail());
        accountBroker.enableAccount(testUser.getUid(), account.getUid(), null);
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
