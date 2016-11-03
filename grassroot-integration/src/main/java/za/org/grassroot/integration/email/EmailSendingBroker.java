package za.org.grassroot.integration.email;

/**
 * Created by luke on 2016/10/24.
 */
public interface EmailSendingBroker {

    void generateAndSendBillingEmail(String emailSubject, String emailBody, String billingRecordUid);

    void sendSystemStatusMail(GrassrootEmail systemStatsEmail);

    void sendMail(GrassrootEmail email);
}
