package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.services.AccountBroker;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Created by luke on 2016/10/25.
 */
@Component
@ConditionalOnProperty(name = "grassroot.email.enabled", havingValue = "true",  matchIfMissing = false)
public class ScheduledEmailTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledEmailTasks.class);

    @Value("${grassroot.daily.admin.email:false}")
    private boolean sendDailyAdminMail;

    private AccountBroker accountBroker;
    private EmailSendingBroker emailSendingBroker;

    @Autowired
    public ScheduledEmailTasks(AccountBroker accountBroker, EmailSendingBroker emailSendingBroker) {
        this.accountBroker = accountBroker;
        this.emailSendingBroker = emailSendingBroker;
    }

    @Scheduled(fixedRate = 1000 * 60 * 60 * 24)
    public void sendSystemStatsEmail() {
        if (sendDailyAdminMail) {
            logger.info("Sending system stats email ... ");
            emailSendingBroker.sendSystemStatusMail(new GrassrootEmail.EmailBuilder("System Email")
                    .content("Hello this is a system email, it will soon have stats and so on").build());
        }
    }

    @Scheduled(cron = "${grassroot.billing.cron.trigger}")
    public void sendMonthlyBillingEmails() {
        logger.info("Sending monthly billing email");
        Instant start = LocalDateTime.now().minus(1, ChronoUnit.MONTHS).toInstant(ZoneOffset.UTC); // todo : store this instant
        Instant end = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        Map<Account, Long> statements = accountBroker.calculateMonthlyStatements(start, end);
        for (Map.Entry<Account, Long> entry : statements.entrySet()) {
            logger.info("statement for account {}, amount {}", entry.getKey().getAccountName(), entry.getValue());
        }
    }


}
