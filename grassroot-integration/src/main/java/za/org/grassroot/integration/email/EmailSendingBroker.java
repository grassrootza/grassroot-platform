package za.org.grassroot.integration.email;

import java.util.List;

/**
 * Created by luke on 2016/10/24.
 */
public interface EmailSendingBroker {

    void generateAndSendBillingEmail(String emailAddress, String emailSubject, String emailBody, List<String> billingRecordUids);

    void sendSystemStatusMail(GrassrootEmail systemStatsEmail);

    void sendMail(GrassrootEmail email);
}
