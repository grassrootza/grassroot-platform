package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.services.account.AccountBillingBroker;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Created by luke on 2016/10/25.
 */
@Component
public class ScheduledAccountTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledAccountTasks.class);

    private AccountBillingBroker accountBillingBroker;

    @Autowired
    public ScheduledAccountTasks(AccountBillingBroker accountBillingBroker) {
        this.accountBillingBroker = accountBillingBroker;
    }

    @Scheduled(cron = "${grassroot.billing.cron.trigger}")
    public void sendMonthlyBillingEmails() {
        logger.info("Sending monthly billing email");
        Instant start = LocalDateTime.now().minus(1, ChronoUnit.MONTHS).toInstant(ZoneOffset.UTC); // todo : store this instant
        Instant end = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        accountBillingBroker.calculateAccountStatements(start, end, true, true);
    }


}
