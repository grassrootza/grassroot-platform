package za.org.grassroot.services.account;

import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.integration.messaging.GrassrootEmail;

import java.util.Set;

/**
 * Created by luke on 2017/03/01.
 */
public interface AccountEmailService {

    String createAccountBillingNotification(AccountBillingRecord record);

    GrassrootEmail createAccountStatementEmail(AccountBillingRecord statement);

    String createEndOfTrialNotification(Account account);

    GrassrootEmail createEndOfTrailEmail(Account account, User adminToEmail, String paymentLink);

    String createDisabledNotification(Account account);

    GrassrootEmail createDisabledEmail(User adminToEmail, String paymentLink);

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
