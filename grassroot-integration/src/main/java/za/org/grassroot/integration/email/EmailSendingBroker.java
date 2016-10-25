package za.org.grassroot.integration.email;

/**
 * Created by luke on 2016/10/24.
 */
public interface EmailSendingBroker {

    void sendSystemStatusMail(GrassrootEmail systemStatsEmail);

    void sendMonthlyBillingStatements();

    void sendMail(GrassrootEmail email);
}
