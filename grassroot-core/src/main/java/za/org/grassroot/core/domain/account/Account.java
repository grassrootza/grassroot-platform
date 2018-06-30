package za.org.grassroot.core.domain.account;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.GrassrootEntity;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
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
@Table(name="paid_account") @Getter @Setter
public class Account implements GrassrootEntity, Serializable {

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

    @ManyToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinTable(name = "account_admins",
            joinColumns = {@JoinColumn(name = "account_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)} )
    private Set<User> administrators = new HashSet<>();

    @OneToMany(mappedBy = "account", fetch = FetchType.LAZY)
    private Set<Group> paidGroups = new HashSet<>();

    @Basic
    @Column(name="free_form_cost")
    private int freeFormCost; // stored as cents

    @Basic
    @Column(name="charge_per_message")
    private boolean billPerMessage;

    @Basic
    @Column(name="payment_reference")
    private String paymentRef;

    @Basic
    @Column(name="subscription_reference")
    private String subscriptionRef;

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
        
        // until the account payment has gone through, do not set it as enabled, but do leave it visible
        this.enabled = false;
        this.enabledDateTime = DateTimeUtil.getVeryLongAwayInstant();
        this.disabledDateTime = DateTimeUtil.getVeryLongAwayInstant();

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
        // todo: figure out why Hibernate is unreliable in setting both sides of this
        this.administrators.add(administrator);
    }

    public void removeAdministrator(User administrator) {
        administrators.remove(administrator);
    }

    public void addPaidGroup(Group group) {
        paidGroups.add(group);
    }

    public void removePaidGroup(Group group) {
        paidGroups.remove(group);
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
                ", enabledDateTime=" + enabledDateTime +
                '}';
    }

    @Override
    public String getName() {
        return accountName;
    }
}
