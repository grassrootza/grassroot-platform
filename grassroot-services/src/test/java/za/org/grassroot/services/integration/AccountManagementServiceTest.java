package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.services.AccountManagementService;

import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@EnableTransactionManagement
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class AccountManagementServiceTest {


    @Autowired
    AccountManagementService accountManagementService;

    @Test
    public void shouldSaveBilling() {
        String email = "bigbucks@money.com";
        Account account = accountManagementService.createAccount("branch1");
        account = accountManagementService.setBillingEmail(account, email);
        assertNotEquals(null,account.getId());
        assertEquals("bigbucks@money.com",account.getPrimaryEmail());
    }

}
