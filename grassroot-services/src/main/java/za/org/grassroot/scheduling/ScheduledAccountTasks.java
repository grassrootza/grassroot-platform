package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountSponsorshipBroker;

/**
 * Created by luke on 2016/10/25.
 */
@Component
@ConditionalOnProperty(name = "grassroot.billing.enabled", havingValue = "true")
public class ScheduledAccountTasks {

    @Value("${grassroot.payments.enabled:false}")
    private boolean paymentsEnabled;

    private static final Logger logger = LoggerFactory.getLogger(ScheduledAccountTasks.class);

    private final AccountBillingBroker accountBillingBroker;
    private final AccountSponsorshipBroker sponsorshipBroker;

    @Autowired
    public ScheduledAccountTasks(AccountBillingBroker accountBillingBroker, AccountSponsorshipBroker sponsorshipBroker) {
        this.accountBillingBroker = accountBillingBroker;
        this.sponsorshipBroker = sponsorshipBroker;
    }

    @Scheduled(cron = "${grassroot.billing.cron.trigger: 0 0 9 * * ?}")
    public void calculateMonthlyBillingAndCosts() {
        logger.info("Calculating billing statements and sending emails and notifications ... ");
        accountBillingBroker.calculateStatementsForDueAccounts(true, true);
    }

    @Scheduled(cron = "${grassroot.payments.cron.trigger: 0 0 20 * * ?}")
    public void processMonthlyBillPayments() {
        if (paymentsEnabled) {
            logger.info("Charging monthly billing amounts");
            accountBillingBroker.processBillsDueForPayment();
        }
    }

    @Scheduled(cron = "${grassroot.payments.cron.trigger: 0 0 10 * * ?}")
    public void cleanupUnansweredSponsorship() {
        logger.info("closing unanswered sponsorship requests & reminding incomplete ones ...");
        sponsorshipBroker.abortAndCleanSponsorshipRequests();
    }

}
