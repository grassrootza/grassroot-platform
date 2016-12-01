package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;

/**
 * Created by luke on 2016/10/25.
 */
@Component
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class ScheduledEmailTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledEmailTasks.class);

    @Value("${grassroot.daily.admin.email:false}")
    private boolean sendDailyAdminMail;

    private EmailSendingBroker emailSendingBroker;

    @Autowired
    public ScheduledEmailTasks(EmailSendingBroker emailSendingBroker) {
        this.emailSendingBroker = emailSendingBroker;
    }

    @Scheduled(cron = "0 0 7 * * ?")
    public void sendSystemStatsEmail() {
        if (sendDailyAdminMail) {
            logger.info("Sending system stats email ... ");
            emailSendingBroker.sendSystemStatusMail(new GrassrootEmail.EmailBuilder("System Email")
                    .content("Hello this is a system email, it will soon have stats and so on").build());
        }
    }


}
