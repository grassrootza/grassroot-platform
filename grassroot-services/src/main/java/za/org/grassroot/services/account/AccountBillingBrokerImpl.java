package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.AccountBillingNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static za.org.grassroot.services.specifications.AccountSpecifications.isValidBetween;
import static za.org.grassroot.services.specifications.AccountSpecifications.nextStatementBefore;
import static za.org.grassroot.services.specifications.NotificationSpecifications.*;

/**
 * Created by luke on 2016/10/25.
 */
@Service
public class AccountBillingBrokerImpl implements AccountBillingBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBillingBrokerImpl.class);

    private static final String SYSTEM_USER = "system_user"; // fake user since user_uid is null and these batch jobs are automated

    private AccountRepository accountRepository;
    private AccountBillingRecordRepository billingRepository;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private EmailSendingBroker emailSendingBroker;

    @Autowired
    public AccountBillingBrokerImpl(AccountRepository accountRepository, AccountBillingRecordRepository billingRepository,
                                    LogsAndNotificationsBroker logsAndNotificationsBroker, EmailSendingBroker emailSendingBroker) {
        this.accountRepository = accountRepository;
        this.billingRepository = billingRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.emailSendingBroker = emailSendingBroker;
    }


    @Override
    @Transactional
    public void calculateAccountStatements(Instant periodStart, Instant periodEnd, boolean sendEmails, boolean sendNotifications) {

        List<Account> validAccounts = accountRepository.findAll(Specifications.where
                (isValidBetween(periodStart, periodEnd)).and
                (nextStatementBefore(Instant.now())));

        log.info("Calculating monthly statements for {} accounts", validAccounts.size());

        List<AccountBillingRecord> records = new ArrayList<>();
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (Account account : validAccounts) {

            long costForPeriod = calculateAccountCostsInPeriod(account.getUid(), periodStart, periodEnd);
            long billForPeriod = account.getSubscriptionFee();

            AccountLog billingLog = new AccountLog.Builder(account)
                    .userUid(SYSTEM_USER)
                    .accountLogType(AccountLogType.BILL_CALCULATED)
                    .billedOrPaid(billForPeriod)
                    .build();
            bundle.addLog(billingLog);

            AccountBillingRecord record = new AccountBillingRecord.BillingBuilder(account)
                    .accountLog(billingLog)
                    .openingBalance(account.getOutstandingBalance())
                    .amountBilled(costForPeriod)
                    .billedPeriodStart(periodStart)
                    .billedPeriodEnd(periodEnd)
                    .statementDateTime(Instant.now())
                    .build();
            records.add(record);

            bundle.addLog(new AccountLog.Builder(account)
                    .userUid(SYSTEM_USER)
                    .accountLogType(AccountLogType.COST_CALCULATED)
                    .billedOrPaid(costForPeriod).build());


            if (sendEmails) {
                emailSendingBroker.sendMail(generateStatementEmail(record));
            }

            if (sendNotifications) {
                bundle.addNotifications(generateStatementNotifications(record));
            }

            account.setNextBillingDate(LocalDateTime.now().plusMonths(1L).toInstant(ZoneOffset.UTC));
        }

        billingRepository.save(records);
        logsAndNotificationsBroker.storeBundle(bundle);
    }


    @Override
    @Transactional(readOnly = true)
    public long calculateAccountCostsInPeriod(String accountUid, Instant periodStart, Instant periodEnd) {
        Account account = accountRepository.findOneByUid(accountUid);

        if (account.getDisabledDateTime().isBefore(periodStart)) {
            return 0;
        }

        // todo : watch Hibernate on this for excessive DB calls (though this is, for the moment, an infrequent batch call)
        Set<PaidGroup> paidGroups = account.getPaidGroups();
        final int messageCost = account.getFreeFormCost();

        Specifications<Notification> notificationCounter = Specifications.where(wasDelivered())
                .and(createdTimeBetween(periodStart, periodEnd))
                .and(belongsToAccount(account));

        long costAccumulator = logsAndNotificationsBroker.countNotifications(notificationCounter) * messageCost;

        for (PaidGroup paidGroup : paidGroups) {
            costAccumulator += (countMessagesForPaidGroup(paidGroup, periodStart, periodEnd) * messageCost);
        }

        return costAccumulator;
    }

    private long countMessagesForPaidGroup(PaidGroup paidGroup, Instant periodStart, Instant periodEnd) {
        Group group = paidGroup.getGroup();
        Instant start = paidGroup.getActiveDateTime().isBefore(periodStart) ? periodStart : paidGroup.getActiveDateTime();
        Instant end = paidGroup.getExpireDateTime() == null || paidGroup.getExpireDateTime().isAfter(periodEnd) ?
                periodEnd : paidGroup.getExpireDateTime();

        Specifications<Notification> specifications = Specifications.where(wasDelivered())
                .and(createdTimeBetween(start, end))
                .and(ancestorGroupIs(group));

        return logsAndNotificationsBroker.countNotifications(specifications);
    }

    private Set<Notification> generateStatementNotifications(AccountBillingRecord billingRecord) {
        Set<Notification> notifications = new HashSet<>();
        for (User user : billingRecord.getAccount().getAdministrators()) {
            AccountBillingNotification notification = new AccountBillingNotification(user, "hello", billingRecord.getAccountLog());
            notification.setNextAttemptTime(Instant.now()); // make sure it's tomorrow morning ...
            notifications.add(notification);
        }
        return notifications;
    }

    private GrassrootEmail generateStatementEmail(AccountBillingRecord billingRecord) {
        return new GrassrootEmail.EmailBuilder("Grassroot Billing Email")
                .address(billingRecord.getAccount().getPrimaryEmail())
                .build();
    }
}
