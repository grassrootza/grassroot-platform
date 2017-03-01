package za.org.grassroot.services.account;

import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.integration.email.GrassrootEmail;

import java.util.Set;

/**
 * Created by luke on 2017/03/01.
 */
public interface AccountEmailService {

    String createAccountBillingNotification(AccountBillingRecord record);

    String createAccountStatementSubject(AccountBillingRecord generatingRecord);

    String createAccountStatementEmail(AccountBillingRecord generatingRecord);

    String createEndOfTrialNotification(Account account);

    String createEndOfTrialEmailSubject();

    String createEndOfTrialEmailBody(Account account, User adminToEmail, String paymentLink);

    String createDisabledNotification(Account account);

    String createDisabledEmailSubject();

    String createDisabledEmailBody(User adminToEmail, String paymentLink);

    /*
    Sponsorship emails, next
     */

    GrassrootEmail createSponsorshipRequestMail(AccountSponsorshipRequest request, User requestingUser,
                                                String messageFromUser, boolean isReminder);

    GrassrootEmail openingUserEmail(boolean alreadyOpen, final String requestLink, final String destinationName, final User openingUser);

    GrassrootEmail sponsorshipDeniedEmail(AccountSponsorshipRequest request);

    GrassrootEmail sponsorshipApprovedEmail(AccountSponsorshipRequest request);

    GrassrootEmail sponsorshipReminderEmailSponsor(AccountSponsorshipRequest request);

    Set<GrassrootEmail> sponsorshipReminderEmailRequestor(AccountSponsorshipRequest request);

}
