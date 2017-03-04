package za.org.grassroot.services.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.services.account.AccountEmailService;

import static org.junit.Assert.assertTrue;

/**
 * Created by luke on 2017/03/04.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class AccountEmailTest {

    private User testUser;
    private User sponsorUser;
    private Account testAccount;

    @Autowired
    private AccountEmailService accountEmailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Before
    public void setUp() {
        String userNumber = "0608880000";
        testUser = new User(userNumber, "test user");
        testUser = userRepository.save(testUser);
        testAccount = new Account(testUser, "Test Account", AccountType.STANDARD,
                testUser, AccountPaymentType.FREE_TRIAL, AccountBillingCycle.MONTHLY);
        // accountRepository.save(testAccount);

        String sponsorNumber = "0605550001";
        sponsorUser = new User(sponsorNumber, "sponsor user");
        sponsorUser.setEmailAddress("someone@somewhere.com");
    }

    @Test
    public void shouldCreateSponsorshipMail() {
        AccountSponsorshipRequest dummyRequest = new AccountSponsorshipRequest(testAccount, sponsorUser, "Please sponsor this");
        GrassrootEmail email = accountEmailService.createSponsorshipRequestMail(dummyRequest, testUser, "Hello does this work", false);
        assertTrue(email.getAddress().equals(sponsorUser.getEmailAddress()));
        assertTrue(email.getHtmlContent().contains("Grassroot"));
    }

}
