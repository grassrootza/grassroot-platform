package za.org.grassroot.core.domain.account;

import za.org.grassroot.core.domain.GrassrootEntity;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2015/10/18. (and significantly overhauled / modified during 2016/10)
 * note: For naming this entity, there could be confusion with a 'user account', but since we rarely use that terminology,
 * better that than 'institution', which seems like it would set us up for trouble (the term is loaded) down the road.
 */

@Entity
@Table(name="paid_account")
public class Account implements GrassrootEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @ManyToOne
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @Basic
    @Column(name="created_date_time", nullable = false, insertable = true, updatable = false)
    private Instant createdDateTime;

    @Basic
    @Column(name="enabled", nullable = false)
    private boolean enabled;

    @ManyToOne
    @JoinColumn(name = "enabled_by_user", nullable = false, updatable = false)
    private User enabledByUser;

    @Basic
    @Column(name = "enabled_date_time", nullable = false)
    private Instant enabledDateTime;

    @ManyToOne
    @JoinColumn(name = "disabled_by_user")
    private User disabledByUser;

    @Column(name = "disabled_date_time", nullable = false)
    private Instant disabledDateTime;
    
    @Basic
    @Column(name = "visible", nullable = false)
    private boolean visible;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(name = "account_admins",
            joinColumns = {@JoinColumn(name = "account_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)} )
    private Set<User> administrators = new HashSet<>();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<PaidGroup> paidGroups = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "billing_user", nullable = false)
    private User billingUser;

    @Basic
    @Column(name = "account_name")
    private String accountName;

    @Basic
    @Column(name = "payment_reference")
    private String paymentRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 50, nullable = false)
    protected AccountType type;

    /*
    Range of account features
     */

    // how many groups the account can set as paid
    @Basic
    @Column(name = "max_group_number")
    private int maxNumberGroups;

    @Basic
    @Column(name = "max_group_size")
    private int maxSizePerGroup;

    @Basic
    @Column(name = "max_sub_group_depth")
    private int maxSubGroupDepth;

    @Basic
    @Column(name = "todos_per_month")
    private int todosPerGroupPerMonth;

    @Basic
    @Column(name = "events_per_month")
    private int eventsPerGroupPerMonth;

    @Basic
    @Column(name="free_form_per_month")
    private int freeFormMessages;

    @Basic
    @Column(name="additional_reminders")
    private int extraReminders;

    /*
    Current state of balance and last payment (amount will be stored in log)
    note : these could also be computed on the fly from logs & billing records, but this provides some redundancy at little overhead, so
     */

    @Basic
    @Column(name="last_payment_date")
    private Instant lastPaymentDate;

    @Basic
    @Column(name="next_billing_date")
    private Instant nextBillingDate;

    @Basic
    @Column(name="outstanding_balance", nullable = false)
    private long outstandingBalance;

    @Basic
    @Column(name="monthly_subscription") // stored in cents, in common with all other costs/figure
    private int subscriptionFee;

    @Basic
    @Column(name="free_form_cost")
    private int freeFormCost; // stored as cents

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 50, nullable = true)
    private AccountPaymentType defaultPaymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 50, nullable = false)
    private AccountBillingCycle billingCycle;

    @Basic
    @Column(name="charge_per_message")
    private boolean billPerMessage;

    @Version
    private Integer version;

    /*
    Constructors
     */

    protected Account() {
        // For JPA
    }

    public Account(User createdByUser, String accountName, AccountType accountType, User billingUser,
                   AccountPaymentType paymentType, AccountBillingCycle billingCycle) {
        Objects.requireNonNull(createdByUser);
        Objects.requireNonNull(billingUser);
        Objects.requireNonNull(accountName);

        this.uid = UIDGenerator.generateId();

        this.accountName = accountName;

        this.createdDateTime = Instant.now();
        
        this.createdByUser = createdByUser;
        this.enabledByUser = createdByUser;
        this.administrators.add(createdByUser);
        
        // until the account payment has gone through, do not set it as enabled, but do leave it visible
        this.visible = true;
        this.enabled = false;
        this.enabledDateTime = DateTimeUtil.getVeryLongAwayInstant();
        this.disabledDateTime = DateTimeUtil.getVeryLongAwayInstant();

        this.type = accountType;
        this.billingUser = billingUser;
        this.defaultPaymentType = paymentType;
        this.billingCycle = billingCycle == null ? AccountBillingCycle.MONTHLY : billingCycle;

        this.outstandingBalance = 0;
    }

    /*
    Getters and setters
     */

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public void setUid(String uid) { this.uid = uid; }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public ZonedDateTime getCreatedDateTimeAtSAST() { return DateTimeUtil.convertToUserTimeZone(createdDateTime, DateTimeUtil.getSAST()); }

    public User getCreatedByUser() { return createdByUser; }

    public void setDisabledByUser(User disabledByUser) { this.disabledByUser = disabledByUser; }

    public void setDisabledDateTime(Instant disabledDateTime) { this.disabledDateTime = disabledDateTime; }

    public User getDisabledByUser() { return disabledByUser; }

    public Instant getDisabledDateTime() { return disabledDateTime; }

    public Set<User> getAdministrators() {
        if (administrators == null) {
            administrators = new HashSet<>();
        }
        return administrators;
    }

    public void setAdministrators(Set<User> administrators) {
        this.administrators = administrators;
    }

    public Set<PaidGroup> getPaidGroups() {
        return paidGroups;
    }

    public Set<PaidGroup> getCurrentPaidGroups() {
        return paidGroups.stream().filter(PaidGroup::isActive).collect(Collectors.toSet());
    }

    public void setPaidGroups(Set<PaidGroup> paidGroups) {
        this.paidGroups = paidGroups;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getName() { return accountName; }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public User getBillingUser() { return billingUser; }

    public void setBillingUser(User billingUser) {
        if (administrators == null) {
            administrators = new HashSet<>();
            administrators.add(billingUser);
        } else {
            administrators.add(billingUser); // since a hashset, duplication not an issue
        }
        this.billingUser = billingUser;
    }

    public User getEnabledByUser() {
        return enabledByUser;
    }

    public Instant getEnabledDateTime() {
        return enabledDateTime;
    }

    public void setEnabledByUser(User enabledByUser) {
        this.enabledByUser = enabledByUser;
    }

    public void setEnabledDateTime(Instant enabledDateTime) {
        this.enabledDateTime = enabledDateTime;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public int getFreeFormMessages() {
        return freeFormMessages;
    }

    public void setFreeFormMessages(int freeFormMessages) {
        this.freeFormMessages = freeFormMessages;
    }

    /*
    Helper methods for adding and removing administrators and groups
     */

    public void addAdministrator(User administrator) {
        // todo: figure out why Hibernate is unreliable in setting both sides of this
        this.administrators.add(administrator);
    }

    public void removeAdministrator(User administrator) {
        administrators.remove(administrator);
    }

    public void addPaidGroup(PaidGroup paidGroup) {
        paidGroups.add(paidGroup);
    }

    public void removePaidGroup(PaidGroup paidGroup) {
        paidGroups.remove(paidGroup);
    }

    public int getMaxNumberGroups() {
        return maxNumberGroups;
    }

    public void setMaxNumberGroups(int maxNumberGroups) {
        this.maxNumberGroups = maxNumberGroups;
    }

    public int getMaxSizePerGroup() {
        return maxSizePerGroup;
    }

    public void setMaxSizePerGroup(int maxSizePerGroup) {
        this.maxSizePerGroup = maxSizePerGroup;
    }

    public int getMaxSubGroupDepth() {
        return maxSubGroupDepth;
    }

    public void setMaxSubGroupDepth(int maxSubGroupDepth) {
        this.maxSubGroupDepth = maxSubGroupDepth;
    }

    public int getExtraReminders() {
        return extraReminders;
    }

    public void setExtraReminders(int extraReminders) {
        this.extraReminders = extraReminders;
    }

    public Integer getVersion() {
        return version;
    }

    public int getFreeFormCost() {
        return freeFormCost;
    }

    public void setFreeFormCost(int freeFormCost) {
        this.freeFormCost = freeFormCost;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Instant getLastPaymentDate() {
        return lastPaymentDate;
    }

    public long getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setLastPaymentDate(Instant lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }

    public void setOutstandingBalance(long outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public void decreaseBalance(long amountToReduce) {
        this.outstandingBalance -= amountToReduce;
    }

    public void addToBalance(long amountBilled) {
            this.outstandingBalance += amountBilled;
    }

    public void removeFromBalance(long amountPaid) {
            this.outstandingBalance -= amountPaid;
    }

    public int getSubscriptionFee() {
        return subscriptionFee;
    }

    public void setSubscriptionFee(int subscriptionFee) {
        this.subscriptionFee = subscriptionFee;
    }

    public int calculatePeriodCost() {
        return subscriptionFee * (isAnnualAccount() ? 12 : 1);
    }

    public Instant getNextBillingDate() {
        return nextBillingDate;
    }

    public void setNextBillingDate(Instant nextBillingDate) {
        this.nextBillingDate = nextBillingDate;
    }

    public void incrementBillingDate(LocalTime billingTime, ZoneOffset billingTz) {
        LocalDate nextDate = LocalDate.now().plus(1, isAnnualAccount() ? ChronoUnit.YEARS : ChronoUnit.MONTHS);
        this.nextBillingDate = nextDate.atTime(billingTime).toInstant(billingTz);
    }

    public String getPaymentRef() { return paymentRef; }

    public void setPaymentRef(String paymentRef) { this.paymentRef = paymentRef; }

    public int getTodosPerGroupPerMonth() {
        return todosPerGroupPerMonth;
    }

    public void setTodosPerGroupPerMonth(int todosPerGroupPerMonth) {
        this.todosPerGroupPerMonth = todosPerGroupPerMonth;
    }

    public int getEventsPerGroupPerMonth() {
        return eventsPerGroupPerMonth;
    }

    public void setEventsPerGroupPerMonth(int eventsPerGroupPerMonth) {
        this.eventsPerGroupPerMonth = eventsPerGroupPerMonth;
    }

    public boolean isBillingActive() { return nextBillingDate != null; }

    public ZonedDateTime getNextBillingDateAtSAST() {
        return nextBillingDate == null ? null : DateTimeUtil.convertToUserTimeZone(nextBillingDate, DateTimeUtil.getSAST());
    }

    public boolean hasPaidInPast() {
        return lastPaymentDate != null;
    }

    public ZonedDateTime getLastPaymentDateAtSAST() {
        return lastPaymentDate == null ? null : DateTimeUtil.convertToUserTimeZone(lastPaymentDate, DateTimeUtil.getSAST());
    }

    public AccountPaymentType getDefaultPaymentType() {
        return defaultPaymentType;
    }

    public void setDefaultPaymentType(AccountPaymentType defaultPaymentType) {
        this.defaultPaymentType = defaultPaymentType;
    }

    public AccountBillingCycle getBillingCycle() {
        return billingCycle;
    }

    public boolean isAnnualAccount() {
        return AccountBillingCycle.ANNUAL.equals(billingCycle);
    }

    public void setBillingCycle(AccountBillingCycle billingCycle) {
        this.billingCycle = billingCycle;
    }

    public boolean isBillPerMessage() {
        return billPerMessage;
    }

    public void setBillPerMessage(boolean billPerMessage) {
        this.billPerMessage = billPerMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account)) {
            return false;
        }

        Account account = (Account) o;

        return getUid() != null ? getUid().equals(account.getUid()) : account.getUid() == null;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Account{" +
                "uid=" + uid +
                ", createdDateTime=" + createdDateTime +
                ", accountName=" + accountName +
                ", enabledDateTime=" + enabledDateTime +
                ", free form messages='" + freeFormMessages + '\'' +
                '}';
    }

}
