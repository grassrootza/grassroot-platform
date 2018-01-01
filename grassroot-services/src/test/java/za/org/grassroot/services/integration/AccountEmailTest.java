package za.org.grassroot.services.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.services.account.AccountEmailService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by luke on 2017/03/04.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@Transactional
public class AccountEmailTest {

    private User testUser;
    private User sponsorUser;
    private Account testAccount;

    private static final String TEST_STRING = "Grassroot";

    @Autowired
    private AccountEmailService accountEmailService;

    @Autowired
    private UserRepository userRepository;

    @Before
    public void setUp() {
        String userNumber = "0608880000";
        testUser = new User(userNumber, "test user", null);
        testUser = userRepository.save(testUser);
        testUser.setEmailAddress("contact@grassroot.org.za");
        testAccount = new Account(testUser, "Test Account", AccountType.STANDARD,
                testUser, AccountPaymentType.FREE_TRIAL, AccountBillingCycle.MONTHLY);
        // accountRepository.save(testAccount);

        String sponsorNumber = "0605550001";
        sponsorUser = new User(sponsorNumber, "sponsor user", null);
        sponsorUser.setEmailAddress("someone@somewhere.com");
    }

    @Test
    @Rollback
    public void shouldCreateStatementEmail() {
        AccountLog dummyLog = new AccountLog.Builder(testAccount)
                .accountLogType(AccountLogType.BILL_CALCULATED)
                .user(testUser)
                .build();
        AccountBillingRecord record = new AccountBillingRecord.BillingBuilder(testAccount)
                .statementDateTime(Instant.now())
                .billedPeriodStart(Instant.now().minus(5, ChronoUnit.DAYS))
                .billedPeriodEnd(Instant.now())
                .paymentDueDate(Instant.now().plus(5, ChronoUnit.HOURS))
                .accountLog(dummyLog)
                .openingBalance(0L)
                .amountBilled(1000L)
                .build();


        GrassrootEmail email = accountEmailService.createAccountStatementEmail(record);
        assertTrue(email.getAddress().equals(testUser.getEmailAddress()));
        assertTrue(email.getHtmlContent().contains(TEST_STRING));
        assertTrue(email.getContent().contains(TEST_STRING));
    }

    @Test
    @Rollback
    public void shouldCreateTrialExpiredEmail() {
        GrassrootEmail email = accountEmailService.createEndOfTrailEmail(testAccount, testUser, "http://something");
        runStandardAssertions(email, testUser.getEmailAddress());
    }

    @Test
    @Rollback
    public void shouldCreateDisabledEmail() {
        GrassrootEmail email = accountEmailService.createDisabledEmail(testUser, "http://payhere");
        runStandardAssertions(email, testUser.getEmailAddress());
    }

    @Test
    @Rollback
    public void shouldCreateSponsorshipMails() {
        AccountSponsorshipRequest dummyRequest = new AccountSponsorshipRequest(testAccount, sponsorUser, "Please sponsor this");
        GrassrootEmail initialEmail = accountEmailService.createSponsorshipRequestMail(dummyRequest, testUser, "Hello does this work", false);
        GrassrootEmail reminderEmail = accountEmailService.createSponsorshipRequestMail(dummyRequest, testUser, "Hello checking reminder", true);

        runStandardAssertions(initialEmail, sponsorUser.getEmailAddress());
        runStandardAssertions(reminderEmail, sponsorUser.getEmailAddress());
    }

    @Test
    @Rollback
    public void shouldCreateEmailForRequestingUser() {
        GrassrootEmail firstMail = accountEmailService.openingUserEmail(false, "http://something", "Sponsor", testUser);
        GrassrootEmail secondMail = accountEmailService.openingUserEmail(true, "http://something", "Sponsor", testUser);

        runStandardAssertions(firstMail, testUser.getEmailAddress());
        runStandardAssertions(secondMail, testUser.getEmailAddress());
    }

    @Test
    @Rollback
    public void shouldCreateDeniedOrApprovedMails() {
        AccountSponsorshipRequest dummyRequest = new AccountSponsorshipRequest(testAccount, sponsorUser, "Please sponsor this");
        GrassrootEmail approvedMail = accountEmailService.sponsorshipApprovedEmail(dummyRequest);
        GrassrootEmail deniedMail = accountEmailService.sponsorshipDeniedEmail(dummyRequest);
        GrassrootEmail reminderMail = accountEmailService.sponsorshipReminderEmailSponsor(dummyRequest);
        Set<GrassrootEmail> reminderMailReq = accountEmailService.sponsorshipReminderEmailRequestor(dummyRequest);

        runStandardAssertions(approvedMail, testUser.getEmailAddress());
        runStandardAssertions(deniedMail, testUser.getEmailAddress());
        runStandardAssertions(reminderMail, sponsorUser.getEmailAddress());
        runStandardAssertions(reminderMailReq.iterator().next(), testUser.getEmailAddress());
    }

    private void runStandardAssertions(GrassrootEmail email, String emailAddress) {
        assertNotNull(email);
//        assertNotNull(email.getAddress());
        assertNotNull(email.getHtmlContent());
        assertNotNull(email.getContent());

        if (email.getAddress() != null) {
            assertTrue(email.getAddress().equals(emailAddress));
        }
        assertTrue(email.getHtmlContent().contains(TEST_STRING));
        assertTrue(email.getContent().contains(TEST_STRING));
    }

}
