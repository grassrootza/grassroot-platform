package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.AccountLog;
import za.org.grassroot.core.domain.notification.AccountBillingNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.repository.AccountBillingRecordRepository;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.email.EmailSendingBroker;
import za.org.grassroot.integration.email.GrassrootEmail;
import za.org.grassroot.integration.payments.PaymentBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.springframework.util.StringUtils.isEmpty;
import static za.org.grassroot.core.specifications.AccountSpecifications.isVisible;
import static za.org.grassroot.core.specifications.AccountSpecifications.nextStatementBefore;
import static za.org.grassroot.core.specifications.BillingSpecifications.*;
import static za.org.grassroot.services.specifications.NotificationSpecifications.*;

/**
 * Created by luke on 2016/10/25.
 */
@Service
public class AccountBillingBrokerImpl implements AccountBillingBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBillingBrokerImpl.class);

    // todo : make sure this can handle both end-of-trial and payment failed
    @Value("${grassroot.trial.end.link:http://localhost:8080/account/signup/start}")
    private String accountPaymentLink;

    private static final String SYSTEM_USER = "system_user"; // fake user since user_uid is null and these batch jobs are automated
    private static final int DEFAULT_MONTH_LENGTH = 30;
    private static final Duration PAYMENT_INTERVAL = Duration.ofHours(1L);

    protected static final ZoneOffset BILLING_TZ = ZoneOffset.UTC;
    protected static final LocalTime STD_BILLING_HOUR = LocalTime.of(10, 0);

    private final AccountRepository accountRepository;

    private final AccountBillingRecordRepository billingRepository;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final AccountEmailService accountEmailService;
    private final ApplicationEventPublisher eventPublisher;

    private final PaymentBroker paymentBroker;
    private EmailSendingBroker emailSendingBroker;

    @Autowired
    public AccountBillingBrokerImpl(AccountRepository accountRepository, AccountBillingRecordRepository billingRepository,
                                    PaymentBroker paymentBroker, ApplicationEventPublisher eventPublisher,
                                    LogsAndNotificationsBroker logsAndNotificationsBroker, AccountEmailService accountEmailService) {
        this.accountRepository = accountRepository;
        this.billingRepository = billingRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.accountEmailService = accountEmailService;
        this.eventPublisher = eventPublisher;
        this.paymentBroker = paymentBroker;
    }

    @Autowired(required = false)
    public void setEmailSendingBroker(EmailSendingBroker emailSendingBroker) {
        this.emailSendingBroker = emailSendingBroker;
    }

    @Override
    @Transactional
    public AccountBillingRecord generateSignUpBill(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);

        AccountBillingRecord lastRecord = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);

        if (lastRecord == null || lastRecord.getPaid()) {
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            long amountToBill = ((account.calculatePeriodCost() + 99) / 100) * 100;
            log.info("calculated amount for sign up bill: {}", amountToBill);
            AccountBillingRecord record = buildInstantBillForAmount(account, amountToBill,
                    "Bill generated for initial account payment", false, bundle);
            record.setStatementDateTime(Instant.now());
            record.setNextPaymentDate(Instant.now());

            account.setOutstandingBalance(amountToBill);

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

        AccountBillingRecord record = buildInstantBillForAmount(account, amountToCharge,
                "Bill generated when switching payment methods", false, bundle);

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
            AccountBillingRecord record = generateStandardBill(account, lastBill, bundle, "Account closing bill");
            record.setNextPaymentDate(Instant.now().plus(PAYMENT_INTERVAL));
            record.setStatementDateTime(Instant.now());

            logsAndNotificationsBroker.storeBundle(bundle);
            billingRepository.save(lastBill);
        }
    }

    @Override
    @Transactional
    public void generateBillOutOfCycle(String accountUid, boolean generateStatement, boolean triggerPayment, Long forceAmount, boolean addToBalance) {
        Account account = accountRepository.findOneByUid(accountUid);
        AccountBillingRecord lastBill = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        AccountBillingRecord record = forceAmount == null ? generateStandardBill(account, lastBill, bundle, null)
                : buildInstantBillForAmount(account, forceAmount, "Bill generated out of cycle", addToBalance, bundle);
        record.setNextPaymentDate(triggerPayment ? Instant.now().plus(PAYMENT_INTERVAL) : null);
        record.setStatementDateTime(generateStatement ? Instant.now() : null);

        if (generateStatement) {
            bundle.addNotifications(generateBillNotifications(record));
            AfterTxCommitTask afterTxCommitTask = () -> processAccountStatement(account, record, triggerPayment);
            eventPublisher.publishEvent(afterTxCommitTask);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
        billingRepository.save(record);
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
            GrassrootEmail billingEmail = accountEmailService.createAccountStatementEmail(generatingBill);
            emailSendingBroker.generateAndSendStatementEmail(billingEmail,
                    records.stream().map(AccountBillingRecord::getUid).collect(Collectors.toList()));
        }
    }

    @Override
    @Transactional
    public void calculateStatementsForDueAccounts(boolean sendEmails, boolean sendNotifications) {
        List<AccountBillingRecord> records = new ArrayList<>();
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        accountRepository.findAll(Specifications.where(isVisible())
                .and(nextStatementBefore(Instant.now())))
                .forEach(a -> {
                    if (AccountPaymentType.FREE_TRIAL.equals(a.getDefaultPaymentType())) {
                        handleFreeTrialStatements(a, bundle, records, sendEmails, sendNotifications);
                    } else {
                        handleCreditCardStatements(a, bundle, records, sendEmails, sendNotifications);
                    }
                });

        logsAndNotificationsBroker.storeBundle(bundle);
        billingRepository.save(records);
    }

    private void handleCreditCardStatements(Account account, LogsAndNotificationsBundle bundle, List<AccountBillingRecord> records,
                                            boolean sendEmails, boolean sendNotifications) {
        DebugUtil.transactionRequired("");
        AccountBillingRecord lastBill = billingRepository.findTopByAccountOrderByCreatedDateTimeDesc(account);
        AccountBillingRecord thisBill = generateStandardBill(account, lastBill, bundle, null);

        thisBill.setStatementDateTime(Instant.now());
        thisBill.setNextPaymentDate(Instant.now().plus(PAYMENT_INTERVAL));
        records.add(thisBill);

        if (sendNotifications) {
            bundle.addNotifications(generateBillNotifications(thisBill));
        }

        account.incrementBillingDate(STD_BILLING_HOUR, BILLING_TZ);
        AfterTxCommitTask afterTxCommitTask = () -> processAccountStatement(account, thisBill, sendEmails);
        eventPublisher.publishEvent(afterTxCommitTask);
    }

    private void handleFreeTrialStatements(Account account, LogsAndNotificationsBundle bundle, List<AccountBillingRecord> records,
                                           boolean sendEmails, boolean sendNotifications) {
        DebugUtil.transactionRequired("");

        log.info("A free trial account has expired, disabling and sending notice ... ");

        long billForPeriod = account.getSubscriptionFee();
        long costForPeriod = calculateAccountCostsInPeriod(account, LocalDateTime.ofInstant(account.getEnabledDateTime(), BILLING_TZ),
                LocalDateTime.now());

        AccountBillingRecord endOfTrialBill = generateBillForAmount(account, billForPeriod, costForPeriod, account.getCreatedDateTime(), bundle,
                "Bill generated at end of free trial");

        endOfTrialBill.setStatementDateTime(Instant.now());
        endOfTrialBill.setNextPaymentDate(null);
        records.add(endOfTrialBill);

        account.setEnabled(false);
        account.setDisabledDateTime(Instant.now());
        account.setNextBillingDate(Instant.now().plus(7L, ChronoUnit.DAYS)); // just to avoid overly annoying them
        // note: do not detach the paid groups from the account, since once the account is paid, will want to re-enable;
        // also, leave it as 'active', else can't distinguish when re-enabling (should maybe rename that field ...)
        log.info("suspending paid groups at end of free trial ... ");
        account.getCurrentPaidGroups().forEach(PaidGroup::suspend);

        if (sendNotifications) {
            bundle.addNotifications(generateTrialExpiredNotifications(account, endOfTrialBill));
        }

        if (sendEmails) {
            sendTrialExpiredEmails(account);
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
            PageRequest singleRecRequest = new PageRequest(0, 1);
            Page<AccountBillingRecord> priorStatement = billingRepository.findAll(baseSpec.and(statementDateNotNull())
                    .and(statementDateBeforeOrderDesc(record.getCreatedDateTime())), singleRecRequest);
            Page<AccountBillingRecord> subseqStatement = billingRepository.findAll(baseSpec.and(statementDateNotNull())
                    .and(statementDateAfterOrderAsc(record.getCreatedDateTime())), singleRecRequest);
            Instant startSearch = (priorStatement == null || !priorStatement.hasContent()) ?
                    account.getCreatedDateTime() : priorStatement.getContent().get(0).getStatementDateTime();
            Instant endSearch = (subseqStatement == null || !subseqStatement.hasContent()) ? Instant.now() :
                    subseqStatement.getContent().get(0).getStatementDateTime();

            setOfRecords.addAll(billingRepository.findAll(baseSpec
                    .and(createdBetween(startSearch, endSearch, true))));
        }

        return setOfRecords.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBillingRecord> loadBillingRecords(String accountUid, boolean unpaidOnly, Sort sort) {
        Account account = accountRepository.findOneByUid(accountUid);
        Specifications<AccountBillingRecord> specs;
        if (!StringUtils.isEmpty(accountUid)) {
            specs = Specifications.where(forAccount(account));
            if (unpaidOnly) {
                specs = specs.and(isPaid(false));
            }
        } else {
            specs = unpaidOnly ? Specifications.where(isPaid(false)) : Specifications.where(paymentDateNotNull());
        }

        return billingRepository.findAll(specs, sort);
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

        Page<AccountBillingRecord> records = billingRepository.findAll(record, new PageRequest(0, 1, Sort.Direction.DESC, "statementDateTime"));

        if (records != null && records.hasContent()) {
            return records.getContent().get(0).getStatementDateTime();
        } else {
            return account.getEnabledDateTime().isBefore(endPeriod) ? account.getCreatedDateTime() : account.getEnabledDateTime();
        }
    }

    @Override
    public AccountBillingRecord fetchRecordByPayment(String paymentId) {
        return billingRepository.findOneByPaymentId(paymentId);
    }

    @Override
    @Transactional
    public void togglePaymentStatus(String recordUid) {
        Objects.requireNonNull(recordUid);
        AccountBillingRecord record = billingRepository.findOneByUid(recordUid);
        record.togglePaid();
    }

    @Override
    @Transactional
    public void processBillsDueForPayment() {
        billingRepository.findAll(Specifications
                .where(paymentDateNotNull())
                .and(isPaid(false))
                .and(paymentType(AccountPaymentType.CARD_PAYMENT))
                .and(paymentDateBefore(Instant.now())))
                .forEach(this::handlePayment);
    }

    private void handlePayment(AccountBillingRecord record) {
        DebugUtil.transactionRequired("");

        boolean transactionSucceeded = paymentBroker.triggerRecurringPayment(record);
        if (!transactionSucceeded) {
            Account account = record.getAccount();
            account.setEnabled(false);
            account.getCurrentPaidGroups().forEach(PaidGroup::suspend);

            logsAndNotificationsBroker.asyncStoreBundle(new LogsAndNotificationsBundle(Collections.emptySet(),
                    accountDisabledNotification(account, record)));
            sendDisabledEmails(account);
        }
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    public void forceUpdateBillingDate(String adminUid, String accountUid, LocalDateTime nextBillingDate) {
        Account account = accountRepository.findOneByUid(accountUid);
        account.setNextBillingDate(nextBillingDate == null ? null : nextBillingDate.toInstant(BILLING_TZ));
        AccountLog accountLog = new AccountLog.Builder(account)
                .userUid(adminUid)
                .accountLogType(AccountLogType.SYSADMIN_BILLING_DATE)
                .description(nextBillingDate == null ? "Stopped billing" : nextBillingDate.toString())
                .build();
        logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(accountLog), Collections.emptySet()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    public void haltAccountPayments(String adminUid, String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);
        billingRepository.findAll(Specifications.where(forAccount(account))
                .and(paymentDateNotNull())
                .and(isPaid(false)))
                .forEach(r -> r.setNextPaymentDate(null));

        AccountLog accountLog = new AccountLog.Builder(account)
                .userUid(adminUid)
                .accountLogType(AccountLogType.SYSADMIN_CHANGED_PAYDATE)
                .description("Halted payments")
                .build();
        logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(accountLog), Collections.emptySet()));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    public void changeBillPaymentDate(String adminUid, String recordUid, LocalDateTime paymentDate) {
        AccountBillingRecord record = billingRepository.findOneByUid(recordUid);
        record.setNextPaymentDate(paymentDate.toInstant(BILLING_TZ));

        AccountLog accountLog = new AccountLog.Builder(record.getAccount())
                .accountLogType(AccountLogType.SYSADMIN_CHANGED_PAYDATE)
                .userUid(adminUid)
                .description("Date: " + paymentDate + ", record UID: " + recordUid)
                .build();
        logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(accountLog), Collections.emptySet()));
    }

    private AccountBillingRecord buildInstantBillForAmount(Account account, long amountToBill, String description,
                                                           boolean addToAccountBalance, LogsAndNotificationsBundle bundle) {
        AccountLog billingLog = new AccountLog.Builder(account)
                .userUid(SYSTEM_USER)
                .accountLogType(AccountLogType.BILL_CALCULATED)
                .billedOrPaid((long) account.getSubscriptionFee())
                .description(description)
                .build();
        bundle.addLog(billingLog);


        AccountBillingRecord record = new AccountBillingRecord.BillingBuilder(account)
                .accountLog(billingLog)
                .openingBalance(account.getOutstandingBalance())
                .amountBilled(amountToBill)
                .billedPeriodStart(Instant.now())
                .billedPeriodEnd(Instant.now())
                .paymentType(account.getDefaultPaymentType())
                .build();

        if (addToAccountBalance) {
            account.addToBalance(amountToBill);
        }

        return record;
    }

    private AccountBillingRecord generateStandardBill(Account account, AccountBillingRecord lastBill, LogsAndNotificationsBundle bundle, String logDescription) {
        Instant periodStart = getPeriodStart(account, lastBill);
        long billForPeriod = calculateAccountBillBetweenDates(account, LocalDateTime.ofInstant(periodStart, BILLING_TZ), LocalDateTime.now());
        long costForPeriod = calculateAccountCostsInPeriod(account, LocalDateTime.ofInstant(periodStart, BILLING_TZ), LocalDateTime.now());
        return generateBillForAmount(account, billForPeriod, costForPeriod, periodStart, bundle, logDescription);
    }

    private AccountBillingRecord generateBillForAmount(Account account, long billForPeriod, long costForPeriod, Instant periodStart,
                                                       LogsAndNotificationsBundle bundle, String logDescription) {

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
                .billedPeriodStart(periodStart)
                .billedPeriodEnd(Instant.now())
                .paymentType(account.getDefaultPaymentType())
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
            return (long) Math.max(Math.ceil(proportionOfMonth * (double) account.getSubscriptionFee()), 0);
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
                    accountEmailService.createAccountBillingNotification(billingRecord),
                    billingRecord.getAccountLog());
            notifications.add(notification);
        }
        return notifications;
    }

    private Set<Notification> generateTrialExpiredNotifications(Account account, AccountBillingRecord bill) {
        Set<Notification> notifications = new HashSet<>();

        account.getAdministrators().forEach(user -> notifications.add(new AccountBillingNotification(user,
                    accountEmailService.createEndOfTrialNotification(account),
                    bill.getAccountLog()))
        );

        return notifications;
    }

    private void sendTrialExpiredEmails(Account account) {
        final String formedPaymentLink = accountPaymentLink + "?accountUid=" + account.getUid();
        account.getAdministrators()
                .stream()
                .filter(User::hasEmailAddress)
                .forEach(u -> emailSendingBroker.sendMail(accountEmailService.createEndOfTrailEmail(account, u, formedPaymentLink)));
    }

    private Set<Notification> accountDisabledNotification(Account account, AccountBillingRecord bill) {
        Set<Notification> notifications = new HashSet<>();
        account.getAdministrators().forEach(user -> {

        });
        return notifications;
    }

    private void sendDisabledEmails(Account account) {
        // note: if ever becomes a heavy load, turn into one email with multiple addresses, but this is a background scheduled
        // job and multiple addresses might result in junk mail, so ...
        final String formedPaymentLink = accountPaymentLink + "?accountUid=" + account.getUid();
        account.getAdministrators()
                .stream()
                .filter(User::hasEmailAddress)
                .forEach(u -> emailSendingBroker.sendMail(accountEmailService.createDisabledEmail(u, formedPaymentLink)));
    }

    private Instant getPeriodStart(Account account, AccountBillingRecord lastBill) {
        return lastBill != null ? lastBill.getBilledPeriodEnd() :
                account.getEnabledDateTime().isBefore(Instant.now()) ? account.getEnabledDateTime() : Instant.now();
    }

}
