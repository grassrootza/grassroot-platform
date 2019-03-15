package za.org.grassroot.core.domain.account;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import za.org.grassroot.core.domain.GrassrootEntity;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Created by luke on 2015/10/18. (and significantly overhauled / modified during 2016/10)
 * note: For naming this entity, there could be confusion with a 'user account', but since we rarely use that terminology,
 * better that than 'institution', which seems like it would set us up for trouble (the term is loaded) down the road.
 */

@Entity
@Table(name="paid_account") @Getter @Setter @Slf4j
public class Account implements GrassrootEntity, Serializable {

    private static final long DEFAULT_SPEND_LIMIT = 10000000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name = "account_name")
    private String accountName;

    @ManyToOne
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @Basic
    @Column(name="created_date_time", nullable = false, insertable = true, updatable = false)
    private Instant createdDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 50, nullable = false)
    protected AccountType type;

    @Basic
    @Column(name="enabled", nullable = false)
    private boolean enabled;

    @Basic
    @Column(name="closed", nullable = false)
    private boolean closed;

    @ManyToOne
    @JoinColumn(name = "enabled_by_user", nullable = false, updatable = false)
    private User enabledByUser;

    @ManyToOne
    @JoinColumn(name = "disabled_by_user")
    private User disabledByUser;

    @Column(name = "disabled_date_time", nullable = false)
    private Instant disabledDateTime;

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(name = "account_admins",
            joinColumns = {@JoinColumn(name = "account_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)} )
    private Set<User> administrators = new HashSet<>();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<Group> paidGroups = new HashSet<>();

    @Basic
    @Column(name="geo_data_sets") // comma separated
    private String geoDataSets; // mirrored on dynamo db, used here to avoid expensive repeat scans, keep track

    @Basic
    @Column(name="free_form_cost")
    private int freeFormCost; // stored as cents, as are all the below

    @Basic
    @Column(name="ussd_avg_cost")
    private int avgUssdCost;

    @Basic
    @Column(name="monthly_fees")
    private long monthlyFlatFee;

    @Basic
    @Column(name="monthly_spending_limit")
    private long monthlySpendingLimit;

    @Basic
    @Column(name="current_month_spend")
    private long currentMonthSpend;

    @Basic
    @Column(name="charge_per_message")
    private boolean billPerMessage;

    @Basic
    @Column(name="payment_reference")
    private String paymentRef;

    @Basic
    @Column(name="subscription_reference", unique = true)
    private String subscriptionRef;

    @Basic
    @Column(name="last_billing_date", nullable = false)
    private Instant lastBillingDate;

    @Basic
    @Column(name="primary_email")
    private String primaryBillingEmail;

    @Version
    private Integer version;

    /*
    Constructors
     */

    protected Account() {
        // For JPA
    }

    public Account(User createdByUser, String accountName, AccountType accountType, User billingUser) {
        Objects.requireNonNull(createdByUser);
        Objects.requireNonNull(billingUser);
        Objects.requireNonNull(accountName);

        this.uid = UIDGenerator.generateId();

        this.accountName = accountName;

        this.createdDateTime = Instant.now();
        
        this.createdByUser = createdByUser;
        this.enabledByUser = createdByUser;
        this.administrators.add(createdByUser);

        this.type = accountType;

        // until the account payment has gone through, do not set it as enabled, but do leave it visible
        this.enabled = false;
        // but equally, this shouldn't be basically gone from anything
        this.closed = false;

        this.disabledDateTime = DateTimeUtil.getVeryLongAwayInstant();

        this.lastBillingDate = this.createdDateTime;

        this.monthlySpendingLimit = DEFAULT_SPEND_LIMIT; // R100,000.00, i.e., basically no limit
        this.currentMonthSpend = 0;
    }

    /*
    Getters and setters
     */
    public Set<User> getAdministrators() {
        if (administrators == null) {
            administrators = new HashSet<>();
        }
        return administrators;
    }

    public void setAdministrators(Set<User> administrators) {
        this.administrators = administrators;
    }

    public Set<Group> getPaidGroups() {
        if (paidGroups == null) {
            paidGroups = new HashSet<>();
        }
        return paidGroups;
    }

    public void setPaidGroups(Set<Group> paidGroups) {
        this.paidGroups = paidGroups;
    }

    /*
    Helper methods for adding and removing administrators and groups
     */

    public void addAdministrator(User administrator) {
        this.administrators.add(administrator);
    }

    public void removeAdministrator(User administrator) {
        administrators.remove(administrator);
    }

    public void addPaidGroup(Group group) {
        paidGroups.add(group);
        group.setAccount(this);
    }

    public void removePaidGroup(Group group) {
        paidGroups.remove(group);
    }

    public boolean sponsorsDataSet() {
        return !StringUtils.isEmpty(geoDataSets);
    }

    public boolean hasBreachedSpendingLimit() {
        log.info("Checking spending limit, current spend: {}, limit: {}", currentMonthSpend, monthlySpendingLimit);
        return false; // until better tested
//        return this.currentMonthSpend > this.monthlySpendingLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Account)) {
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
                ", enabled=" + enabled +
                '}';
    }

    @Override
    public String getName() {
        return accountName;
    }
}
