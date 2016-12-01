package za.org.grassroot.services.account;

import org.jivesoftware.smack.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.AccountBillingNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.specifications.AccountSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.springframework.util.StringUtils.isEmpty;
import static za.org.grassroot.core.specifications.AccountSpecifications.*;
import static za.org.grassroot.services.specifications.NotificationSpecifications.*;

/**
 * Created by luke on 2016/10/25.
 */
@Service
public class AccountBillingBrokerImpl implements AccountBillingBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBillingBrokerImpl.class);

    private static final String SYSTEM_USER = "system_user"; // fake user since user_uid is null and these batch jobs are automated
    private static final int DEFAULT_MONTH_LENGTH = 30;
    private static final Duration PAYMENT_INTERVAL = Duration.ofHours(1L);

    protected static final ZoneOffset BILLING_TZ = ZoneOffset.UTC;
    protected static final LocalTime STD_BILLING_HOUR = LocalTime.of(10, 0);

    private final AccountRepository accountRepository;

    private final AccountBillingRecordRepository billingRepository;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final MessageAssemblingService messageAssemblingService;
    private final ApplicationEventPublisher eventPublisher;

    private final EmailSendingBroker emailSendingBroker;
    private final Environment environment;

    @Autowired
    public AccountBillingBrokerImpl(AccountRepository accountRepository, AccountBillingRecordRepository billingRepository,
                                    LogsAndNotificationsBroker logsAndNotificationsBroker, MessageAssemblingService messageAssemblingService,
                                    ApplicationEventPublisher eventPublisher, EmailSendingBroker emailSendingBroker, Environment environment) {
        this.accountRepository = accountRepository;
        this.billingRepository = billingRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.messageAssemblingService = messageAssemblingService;
        this.eventPublisher = eventPublisher;
        this.emailSendingBroker = emailSendingBroker;
        this.environment = environment;
    }

    @Override
    @Transactional
    public AccountBillingRecord generateSignUpBill(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);

        AccountBillingRecord lastRecord = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);

        if (lastRecord == null || lastRecord.getPaid()) {
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            AccountBillingRecord record = buildInstantBillForAmount(account, (long) account.getSubscriptionFee(),
                    "Bill generated for initial account payment", bundle);
            record.setStatementDateTime(Instant.now());
            record.setNextPaymentDate(Instant.now());

            account.setOutstandingBalance(account.getSubscriptionFee());

            logsAndNotificationsBroker.storeBundle(bundle);
            return billingRepository.save(record);
        } else {
            return lastRecord;
        }
    }

    @Override
    @Transactional
    public AccountBillingRecord generatePaymentChangeBill(String accountUid, long amountToCharge) {
        Account account = accountRepository.findOneByUid(accountUid);

        log.info("Generating payment change bill, amount is : " + amountToCharge);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        AccountBillingRecord lastBill = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);
        AccountBillingRecord record = buildInstantBillForAmount(account, amountToCharge,
                "Bill generated when switching payment methods", bundle);

        // note : do not change account balance as this payment is not for sub, and hence should put account in credit
        record.setStatementDateTime(null);
        record.setNextPaymentDate(Instant.now());
        record.setTotalAmountToPay(amountToCharge);

        logsAndNotificationsBroker.storeBundle(bundle);
        return billingRepository.save(record);
    }

    @Override
    @Transactional
    public void generateClosingBill(String userUid, String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);

        boolean hasPaidPrior = billingRepository.countByAccountAndPaidTrue(account) > 0;
        if (hasPaidPrior) {
            AccountBillingRecord lastBill = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);

            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            AccountBillingRecord record = generateBillSince(account, lastBill, bundle, "Account closing bill");
            record.setNextPaymentDate(Instant.now().plus(PAYMENT_INTERVAL));
            record.setStatementDateTime(Instant.now());

            logsAndNotificationsBroker.storeBundle(bundle);
            billingRepository.save(lastBill);
        }
    }

    @Override
    @Transactional
    public void generateBillOutOfCycle(String accountUid, boolean generateStatement, boolean triggerPayment) {
        Account account = accountRepository.findOneByUid(accountUid);
        AccountBillingRecord lastBill = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        AccountBillingRecord record = generateBillSince(account, lastBill, bundle, null);
        record.setNextPaymentDate(triggerPayment ? Instant.now().plus(PAYMENT_INTERVAL) : null);
        record.setStatementDateTime(generateStatement ? Instant.now() : null);

        if (generateStatement) {
            bundle.addNotifications(generateBillNotifications(record));
            AfterTxCommitTask afterTxCommitTask = () -> processAccountStatement(account, record, true);
            eventPublisher.publishEvent(afterTxCommitTask);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        billingRepository.save(record);
    }

    @Override
    @Transactional
    public void calculateStatementsForDueAccounts(boolean sendEmails, boolean sendNotifications) {

        List<Account> accountsForStatements = accountRepository.findAll((nextStatementBefore(Instant.now())));

        List<AccountBillingRecord> records = new ArrayList<>();
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (Account account : accountsForStatements) {
            AccountBillingRecord lastBill = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);
            AccountBillingRecord thisBill = generateBillSince(account, lastBill, bundle, null);

            thisBill.setStatementDateTime(Instant.now());
            thisBill.setNextPaymentDate(Instant.now().plus(PAYMENT_INTERVAL));
            records.add(thisBill);

            if (sendNotifications) {
                bundle.addNotifications(generateBillNotifications(thisBill));
            }

            account.setNextBillingDate(LocalDateTime.now().plusMonths(1L).toInstant(ZoneOffset.UTC));
            AfterTxCommitTask afterTxCommitTask = () -> processAccountStatement(account, thisBill, sendEmails);
            eventPublisher.publishEvent(afterTxCommitTask);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        billingRepository.save(records);
    }

    @Override
    @Transactional(readOnly = true)
    public void processAccountStatement(Account account, AccountBillingRecord generatingBill, boolean sendEmail) {
        Objects.requireNonNull(account);
        if (generatingBill.getStatementDateTime() == null) {
            throw new IllegalArgumentException("Error! Statement must have a statement-generating bill");
        }

        final String emailAddress = account.getBillingUser().getEmailAddress();
        if (sendEmail && !StringUtils.isEmpty(emailAddress)) {
            List<AccountBillingRecord> records = findRecordsToIncludeInStatement(account,
                    generatingBill.getStatementDateTime().plus(1, ChronoUnit.MINUTES)); // just in case cutting it too fine excludes this one
            final String emailSubject = messageAssemblingService.createAccountStatementSubject(generatingBill);
            final String emailBody = messageAssemblingService.createAccountStatementEmail(generatingBill);
            emailSendingBroker.generateAndSendBillingEmail(emailAddress, emailSubject, emailBody,
                    records.stream().map(AccountBillingRecord::getUid).collect(Collectors.toList()));
        }

    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBillingRecord> findRecordsWithStatementDates(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);
        List<AccountBillingRecord> billingRecords = billingRepository.findAll(Specifications.where(forAccount(account))
                .and(statementDateNotNull()));
        billingRecords.sort(Comparator.reverseOrder());
        return billingRecords;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBillingRecord> findRecordsInSameStatementCycle(String recordUid) {
        AccountBillingRecord record = billingRepository.findOneByUid(recordUid);
        Set<AccountBillingRecord> setOfRecords = new HashSet<>();
        setOfRecords.add(record);

        if (record.getStatementDateTime() != null) {
            setOfRecords.addAll(findRecordsToIncludeInStatement(record.getAccount(), record.getStatementDateTime()));
        } else {
            Account account = record.getAccount();
            Specifications<AccountBillingRecord> baseSpec = Specifications.where(forAccount(account));
            AccountBillingRecord priorStatement = billingRepository.findOne(baseSpec.and(statementDateNotNull())
                    .and(statementDateBeforeOrderDesc(record.getCreatedDateTime())));
            AccountBillingRecord subseqStatement = billingRepository.findOne(baseSpec.and(statementDateNotNull())
                    .and(statementDateAfterOrderAsc(record.getCreatedDateTime())));
            Instant startSearch = priorStatement == null ? account.getCreatedDateTime() : priorStatement.getStatementDateTime();
            Instant endSearch = subseqStatement == null ? Instant.now() : subseqStatement.getStatementDateTime();

            setOfRecords.addAll(billingRepository.findAll(baseSpec
                    .and(createdBetween(startSearch, endSearch, true))));
        }

        return setOfRecords.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    private List<AccountBillingRecord> findRecordsToIncludeInStatement(Account account, Instant endPeriod) {
        List<AccountBillingRecord> billsSince = billingRepository.findAll(Specifications.where(forAccount(account))
                .and(createdBetween(getPriorStatementTime(account, endPeriod), endPeriod, false)));
        billsSince.sort(Comparator.reverseOrder());
        return billsSince;
    }

    private Instant getPriorStatementTime(Account account, Instant endPeriod) {
        Specifications<AccountBillingRecord> record = Specifications.where(forAccount(account))
                .and(statementDateNotNull())
                .and(statementDateBeforeOrderDesc(endPeriod));

        AccountBillingRecord lastStatement = billingRepository.findOne(record);
        if (lastStatement != null) {
            return lastStatement.getStatementDateTime();
        } else {
            return account.getEnabledDateTime().isBefore(endPeriod) ?
                    account.getCreatedDateTime() : account.getEnabledDateTime();
        }
    }

    @Override
    public AccountBillingRecord fetchRecordByPayment(String paymentId) {
        return billingRepository.findOneByPaymentId(paymentId);
    }

    @Override
    @Transactional
    public void resetAccountStatementDatesForTesting() {
        if (!environment.acceptsProfiles("production")) {
            accountRepository.findAll(AccountSpecifications.isEnabled())
                    .forEach(account -> account.setNextBillingDate(Instant.now()));
        }
    }

    private AccountBillingRecord buildInstantBillForAmount(Account account, long amountToBill, String description, LogsAndNotificationsBundle bundle) {
        AccountLog billingLog = new AccountLog.Builder(account)
                .userUid(SYSTEM_USER)
                .accountLogType(AccountLogType.BILL_CALCULATED)
                .billedOrPaid((long) account.getSubscriptionFee())
                .description("Bill generated for initial account payment")
                .build();
        bundle.addLog(billingLog);

        return new AccountBillingRecord.BillingBuilder(account)
                .accountLog(billingLog)
                .openingBalance(account.getOutstandingBalance())
                .amountBilled(amountToBill)
                .billedPeriodStart(Instant.now())
                .billedPeriodEnd(Instant.now())
                .build();
    }

    private AccountBillingRecord generateBillSince(Account account, AccountBillingRecord lastBill, LogsAndNotificationsBundle bundle, String logDescription) {
        long billForPeriod = calculateAccountBillBetweenDates(account, LocalDateTime.ofInstant(lastBill.getBilledPeriodEnd(), BILLING_TZ),
                LocalDateTime.now());
        long costForPeriod = calculateAccountCostsInPeriod(account, LocalDateTime.ofInstant(lastBill.getBilledPeriodEnd(), BILLING_TZ),
                LocalDateTime.now());

        AccountLog.Builder billingLogBuilder = new AccountLog.Builder(account)
                .userUid(SYSTEM_USER)
                .accountLogType(AccountLogType.BILL_CALCULATED)
                .billedOrPaid(billForPeriod);

        if (!isEmpty(logDescription)) {
            billingLogBuilder = billingLogBuilder.description(logDescription);
        }

        AccountLog billingLog = billingLogBuilder.build();
        bundle.addLog(billingLog);

        AccountLog costLog = new AccountLog.Builder(account)
                .userUid(SYSTEM_USER)
                .accountLogType(AccountLogType.COST_CALCULATED)
                .billedOrPaid(costForPeriod)
                .build();
        bundle.addLog(costLog);

        AccountBillingRecord record = new AccountBillingRecord.BillingBuilder(account)
                .accountLog(billingLog)
                .openingBalance(account.getOutstandingBalance())
                .amountBilled(billForPeriod)
                .billedPeriodStart(lastBill.getBilledPeriodEnd())
                .billedPeriodEnd(Instant.now())
                .build();

        account.addToBalance(billForPeriod);

        return record;
    }

    private long calculateAccountBillBetweenDates(Account account, LocalDateTime billingPeriodStart, LocalDateTime billingPeriodEnd) {
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

    private Set<Notification> generateBillNotifications(AccountBillingRecord billingRecord) {
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
}
