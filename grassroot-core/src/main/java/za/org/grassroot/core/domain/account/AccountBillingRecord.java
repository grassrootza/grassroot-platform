package za.org.grassroot.core.domain.account;

import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Created by luke on 2016/10/25.
 * Created because billing & payment are sufficiently important that we need more than just a log
 */
@Entity
@Table(name = "paid_account_billing")
public class AccountBillingRecord implements Comparable<AccountBillingRecord> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @OneToOne
    @JoinColumn(name = "account_log_id", nullable = false, updatable = false)
    private AccountLog accountLog;

    @Basic
    @Column(name="created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    @Basic
    @Column(name="statement_date_time")
    private Instant statementDateTime;

    @Basic
    @Column(name="billed_period_start", nullable = false, updatable = false)
    private Instant billedPeriodStart;

    @Basic
    @Column(name="billed_period_end", nullable = false, updatable = false)
    private Instant billedPeriodEnd;

    @Basic
    @Column(name="opening_balance", nullable = false, updatable = false)
    private long openingBalance;

    @Basic
    @Column(name="amount_billed", nullable = false, updatable = false)
    private long amountBilledThisPeriod;

    @Basic
    @Column(name="billed_balance", nullable = false, updatable = false)
    private long totalAmountToPay;

    @Basic
    @Column(name="next_payment_date")
    private Instant nextPaymentDate;

    @Basic
    @Column(name="bill_paid")
    private boolean paid;

    @Basic
    @Column(name="paid_date")
    private Instant paidDate;

    @Basic
    @Column(name="paid_amount")
    private Long paidAmount;

    @Basic
    @Column(name="payment_id", length = 50)
    private String paymentId;

    @Basic
    @Column(name="payment_desc")
    private String paymentDescription;

    // defaults to the account default, but leaving flexibility to override if necessary
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 50, nullable = true)
    protected AccountPaymentType paymentType;

    private AccountBillingRecord() {
        // for JPA
    }

    @Override
    public int compareTo(AccountBillingRecord record) {
        return createdDateTime.compareTo(record.createdDateTime);
    }

    public static class BillingBuilder {
        private Account account;
        private AccountLog accountLog;
        private Instant statementDateTime;
        private Instant billedPeriodStart;
        private Instant billedPeriodEnd;
        private Long openingBalance;
        private Long amountBilled;
        private Instant paymentDueDate;
        private AccountPaymentType paymentType;

        public BillingBuilder(Account account) {
            this.account = account;
        }

        public BillingBuilder accountLog(AccountLog accountLog) {
            this.accountLog = accountLog;
            return this;
        }

        public BillingBuilder statementDateTime(Instant statementDateTime) {
            this.statementDateTime = statementDateTime;
            return this;
        }

        public BillingBuilder billedPeriodStart(Instant billedPeriodStart) {
            this.billedPeriodStart = billedPeriodStart;
            return this;
        }

        public BillingBuilder billedPeriodEnd(Instant billedPeriodEnd) {
            this.billedPeriodEnd = billedPeriodEnd;
            return this;
        }

        public BillingBuilder openingBalance(Long openingBalance) {
            this.openingBalance = openingBalance;
            return this;
        }

        public BillingBuilder amountBilled(Long amountBilled) {
            this.amountBilled = amountBilled;
            return this;
        }

        public BillingBuilder paymentDueDate(Instant paymentDueDate) {
            this.paymentDueDate = paymentDueDate;
            return this;
        }

        public BillingBuilder paymentType(AccountPaymentType paymentType) {
            this.paymentType = paymentType;
            return this;
        }

        public AccountBillingRecord build() {
            AccountBillingRecord record = new AccountBillingRecord(account, accountLog, statementDateTime, billedPeriodStart, billedPeriodEnd,
                    openingBalance, amountBilled);
            if (paymentDueDate != null) {
                record.setNextPaymentDate(paymentDueDate);
            }
            if (paymentType != null) {
                record.setPaymentType(paymentType);
            }
            return record;
        }
    }

    private AccountBillingRecord(Account account, AccountLog accountLog, Instant statementDateTime, Instant billedPeriodStart,
                                 Instant billedPeriodEnd, Long openingBalance, Long amountBilled) {

        Objects.requireNonNull(account);
        Objects.requireNonNull(accountLog);
        Objects.requireNonNull(billedPeriodStart);
        Objects.requireNonNull(billedPeriodEnd);
        Objects.requireNonNull(openingBalance);
        Objects.requireNonNull(amountBilled);

        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();

        this.account = account;
        this.accountLog = accountLog;
        this.statementDateTime = statementDateTime;
        this.billedPeriodStart = billedPeriodStart;
        this.billedPeriodEnd = billedPeriodEnd;
        this.openingBalance = openingBalance;
        this.amountBilledThisPeriod = amountBilled;

        this.totalAmountToPay = openingBalance + amountBilled;
        this.paymentType = account.getDefaultPaymentType();

        this.paid = false;
    }

    public Long getId() { return id; }

    public String getUid() { return uid; }

    public Account getAccount() {
        return account;
    }

    public AccountLog getAccountLog() { return accountLog; }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public Instant getStatementDateTime() {
        return statementDateTime;
    }

    public Instant getBilledPeriodStart() {
        return billedPeriodStart;
    }

    public Instant getBilledPeriodEnd() {
        return billedPeriodEnd;
    }

    public long getOpeningBalance() {
        return openingBalance;
    }

    public long getAmountBilledThisPeriod() {
        return amountBilledThisPeriod;
    }

    public void setTotalAmountToPay(long totalAmountToPay) {
        this.totalAmountToPay = totalAmountToPay;
    }

    public long getTotalAmountToPay() {
        return totalAmountToPay;
    }

    public Instant getNextPaymentDate() {
        return nextPaymentDate;
    }

    public void setNextPaymentDate(Instant nextPaymentDate) {
        this.nextPaymentDate = nextPaymentDate;
    }

    public boolean getPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public void togglePaid() {
        paid = !paid;
    }

    public Instant getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(Instant paidDate) {
        this.paidDate = paidDate;
    }

    public Long getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(Long paidAmount) {
        this.paidAmount = paidAmount;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public void setStatementDateTime(Instant statementDateTime) {
        this.statementDateTime = statementDateTime;
    }

    public String getPaymentDescription() {
        return paymentDescription;
    }

    public void setPaymentDescription(String paymentDescription) {
        this.paymentDescription = paymentDescription;
    }

    public AccountPaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(AccountPaymentType paymentType) {
        this.paymentType = paymentType;
    }

    // for Thymeleaf
    public LocalDateTime getStatementDate() {
        return LocalDateTime.ofInstant(statementDateTime, ZoneId.systemDefault());
    }

    @Override
    public String toString() {
        return "AccountBillingRecord{" +
                "createdDateTime=" + createdDateTime +
                ", statementDateTime=" + statementDateTime +
                ", openingBalance=" + openingBalance +
                ", amountBilledThisPeriod=" + amountBilledThisPeriod +
                ", totalAmountToPay=" + totalAmountToPay +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AccountBillingRecord record = (AccountBillingRecord) o;

        return uid.equals(record.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }
}
