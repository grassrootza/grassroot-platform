package za.org.grassroot.integration.email;

import java.util.List;

/**
 * Created by luke on 2016/10/24.
 */
public interface EmailSendingBroker {

    void generateAndSendStatementEmail(GrassrootEmail baseMail, List<String> billingRecordsToIncludeByUid);

    void sendSystemStatusMail(GrassrootEmail systemStatsEmail);

    void sendMail(GrassrootEmail email);

}
