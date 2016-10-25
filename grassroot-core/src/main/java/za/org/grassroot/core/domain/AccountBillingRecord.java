package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2016/10/25.
 * Created because billing & payment are sufficiently important that we need more than just a log
 */
@Entity
@Table(name = "paid_account_billing")
public class AccountBillingRecord {

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
    @Column(name="statement_date_time", nullable = false, updatable = false)
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
    private long amountBilled;

    @Basic
    @Column(name="billed_balance", nullable = false, updatable = false)
    private long billedBalance;

    private AccountBillingRecord() {
        // for JPA
    }

    public static class BillingBuilder {
        private Account account;
        private AccountLog accountLog;
        private Instant statementDateTime;
        private Instant billedPeriodStart;
        private Instant billedPeriodEnd;
        private Long openingBalance;
        private Long amountBilled;

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

        public AccountBillingRecord build() {
            return new AccountBillingRecord(account, accountLog, statementDateTime, billedPeriodStart, billedPeriodEnd, openingBalance, amountBilled);
        }
    }

    private AccountBillingRecord(Account account, AccountLog accountLog, Instant statementDateTime, Instant billedPeriodStart,
                                 Instant billedPeriodEnd, Long openingBalance, Long amountBilled) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(accountLog);
        Objects.requireNonNull(statementDateTime);
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
        this.amountBilled = amountBilled;

        this.billedBalance = openingBalance + amountBilled;
    }

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

    public long getAmountBilled() {
        return amountBilled;
    }

    public long getBilledBalance() {
        return billedBalance;
    }
}
