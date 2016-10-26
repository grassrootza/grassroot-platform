package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.AccountBillingNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static za.org.grassroot.services.specifications.AccountSpecifications.nextStatementBefore;
import static za.org.grassroot.services.specifications.NotificationSpecifications.*;

/**
 * Created by luke on 2016/10/25.
 */
@Service
public class AccountBillingBrokerImpl implements AccountBillingBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBillingBrokerImpl.class);

    private static final String SYSTEM_USER = "system_user"; // fake user since user_uid is null and these batch jobs are automated
    private static final ZoneOffset BILLING_TZ = ZoneOffset.UTC;
    private static final int DEFAULT_MONTH_LENGTH = 30;

    private AccountRepository accountRepository;
    private AccountBillingRecordRepository billingRepository;

    private EmailSendingBroker emailSendingBroker;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private MessageAssemblingService messageAssemblingService;

    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public AccountBillingBrokerImpl(AccountRepository accountRepository, AccountBillingRecordRepository billingRepository,
                                    LogsAndNotificationsBroker logsAndNotificationsBroker, EmailSendingBroker emailSendingBroker,
                                    MessageAssemblingService messageAssemblingService, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.billingRepository = billingRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.emailSendingBroker = emailSendingBroker;
        this.messageAssemblingService = messageAssemblingService;
        this.eventPublisher = eventPublisher;
    }


    @Override
    @Transactional
    public void calculateAccountStatements(boolean sendEmails, boolean sendNotifications) {

        List<Account> validAccounts = accountRepository.findAll(
                (nextStatementBefore(Instant.now())));

        log.info("Calculating monthly statements for {} accounts", validAccounts.size());

        List<AccountBillingRecord> records = new ArrayList<>();
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (Account account : validAccounts) {
            log.debug("Calculating bill and cost for account : {}", account.getAccountName());
            AccountBillingRecord lastBill = billingRepository.findOneByAccountOrderByCreatedDateTimeDesc(account);

            /*
            * Logic : billing starts at the last statement end date, if there is a last statement; if not, it defaults to
            * the account enabled date, or one month ago, whichever is later
            * Note: by default when an account is enabled a billing record is generated & the cycle defaults to monthly, so
            * the other two cases should not occur, but accommodating possible corner cases
            */

            LocalDateTime monthAgo = LocalDateTime.now().minusMonths(1L);
            LocalDateTime billingStart = (lastBill != null) ? LocalDateTime.ofInstant(lastBill.getBilledPeriodEnd(), BILLING_TZ)
                    : account.getEnabledDateTime().isBefore(monthAgo.toInstant(BILLING_TZ)) ? monthAgo
                    : LocalDateTime.ofInstant(account.getEnabledDateTime(), BILLING_TZ);

            LocalDateTime billingEnd = Instant.now().isBefore(account.getDisabledDateTime()) ?
                    LocalDateTime.now() : LocalDateTime.ofInstant(account.getDisabledDateTime(), BILLING_TZ);

            log.debug("Calculating bill and cost since : {}", billingStart);

            long billForPeriod = calculateAccountBillSinceLastStatement(account, billingStart, billingEnd);
            long costForPeriod = calculateAccountCostsInPeriod(account, billingStart, billingEnd);

            log.debug("Okay, calculated bill = {}, cost = {}", billForPeriod, costForPeriod);

            AccountLog billingLog = new AccountLog.Builder(account)
                    .userUid(SYSTEM_USER)
                    .accountLogType(AccountLogType.BILL_CALCULATED)
                    .billedOrPaid(billForPeriod)
                    .build();
            bundle.addLog(billingLog);

            AccountBillingRecord record = new AccountBillingRecord.BillingBuilder(account)
                    .accountLog(billingLog)
                    .openingBalance(account.getOutstandingBalance())
                    .amountBilled(billForPeriod)
                    .billedPeriodStart(billingStart.toInstant(BILLING_TZ))
                    .billedPeriodEnd(billingEnd.toInstant(BILLING_TZ))
                    .statementDateTime(Instant.now())
                    .build();
            records.add(record);

            bundle.addLog(new AccountLog.Builder(account)
                    .userUid(SYSTEM_USER)
                    .accountLogType(AccountLogType.COST_CALCULATED)
                    .billedOrPaid(costForPeriod).build());

            if (sendNotifications) {
                bundle.addNotifications(generateStatementNotifications(record));
            }

            account.setNextBillingDate(LocalDateTime.now().plusMonths(1L).toInstant(ZoneOffset.UTC));
            account.addToBalance(billForPeriod);

            log.info("Set account next billing date : " + account.getNextBillingDate());
        }

        if (sendEmails && !records.isEmpty()) {
            AfterTxCommitTask afterTxCommitTask = () -> processAccountStatementEmails(
                    records.stream().map(AccountBillingRecord::getUid).collect(Collectors.toSet()));
            eventPublisher.publishEvent(afterTxCommitTask);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        billingRepository.save(records);
    }

    private long calculateAccountBillSinceLastStatement(Account account, LocalDateTime billingPeriodStart, LocalDateTime billingPeriodEnd) {
        // note : be careful about not running this around midnight, or date calcs could get messy / false (and keep an eye on floating points)
        if (DateTimeUtil.areDatesOneMonthApart(billingPeriodStart, billingPeriodEnd)) {
            return account.getSubscriptionFee();
        } else {
            double proportionOfMonth = (double) (DAYS.between(billingPeriodStart, billingPeriodEnd)) / (double) DEFAULT_MONTH_LENGTH;
            log.info("Proportion of month: {}", proportionOfMonth);
            return (long) Math.ceil(proportionOfMonth * (double) account.getSubscriptionFee());
        }
    }

    private long calculateAccountCostsInPeriod(Account account, LocalDateTime billingPeriodStart, LocalDateTime billingPeriodEnd) {
        Instant periodStart = billingPeriodStart.toInstant(BILLING_TZ);
        Instant periodEnd = billingPeriodEnd.toInstant(BILLING_TZ);

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
            AccountBillingNotification notification = new AccountBillingNotification(user,
                    messageAssemblingService.createAccountBillingNotification(billingRecord),
                    billingRecord.getAccountLog());
            notification.setNextAttemptTime(Instant.now()); // make sure it's tomorrow morning ...
            notifications.add(notification);
        }
        return notifications;
    }

    @Override
    @Transactional
    public void processAccountStatementEmails(Set<String> billingRecordUids) {
        Objects.requireNonNull(billingRecordUids);

        log.debug("Inside send account statement emails");

        Set<AccountBillingRecord> records = billingRepository.findByUidIn(billingRecordUids);

        for (AccountBillingRecord record : records) {
            log.debug("generating account statement for {}, amount of {}", record.getAccount().getAccountName(), record.getBilledBalance());
            emailSendingBroker.sendMail(generateStatementEmail(record));
        }
    }

    @Override
    public boolean chargeAccountStatement(String accountUid, String billingRecordUid) {
        return false;
    }

    private GrassrootEmail generateStatementEmail(AccountBillingRecord billingRecord) {
        final String emailSubject = messageAssemblingService.createAccountStatementSubject(billingRecord);
        final String emailBody = messageAssemblingService.createAccountStatementEmail(billingRecord);

        return new GrassrootEmail.EmailBuilder(emailSubject)
                .content(emailBody)
                .address(billingRecord.getAccount().getBillingUser().getEmailAddress())
                .build();
    }
}
