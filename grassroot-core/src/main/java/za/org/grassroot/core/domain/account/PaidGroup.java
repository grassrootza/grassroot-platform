package za.org.grassroot.core.domain.account;

import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.PaidGroupStatus;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2015/10/20.
 */
@Entity
@Table(name="paid_group", indexes = {
        @Index(name = "idx_paid_group_origin_group", columnList = "group_id"),
        @Index(name = "idx_paid_group_account", columnList = "account_id"),
        @Index(name = "idx_paid_group_status", columnList = "status")})
public class PaidGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    /*
    Entity may get created before it is made active, hence the two different timestamps
     */
    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    /*
    Since this is a record for a specific duration of time, we have many-to-one on groups (so, one group can be paid
    by a certain account for a certain period, a different one later, etc., but only ever one at a time).
     */
    @ManyToOne
    @JoinColumn(name="group_id")
    private Group group;

    @ManyToOne
    @JoinColumn(name="account_id")
    private Account account;

    @Basic
    @Column(name="active_date_time")
    private Instant activeDateTime;

    @ManyToOne
    @JoinColumn(name="user_added_id")
    private User addedByUser;

    @ManyToOne
    @JoinColumn(name="user_removed_id", nullable = true)
    private User removedByUser;

    @Basic
    @Column(name="expire_date_time")
    private Instant expireDateTime;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    protected PaidGroupStatus status;

    /*
    Constructors
     */

    /*
    If we are just passed the group and account, assume that active time is now, expiry time forever
     */

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    public PaidGroup(Group group, Account account, User addedByUser) {
        Objects.requireNonNull(group);
        Objects.requireNonNull(account);
        Objects.requireNonNull(addedByUser);

        if (StringUtils.isEmpty(group.getGroupName())) {
            throw new IllegalArgumentException("Error! Group must have a name to be paid for");
        }

        if (StringUtils.isEmpty(account.getAccountName())) {
            throw new IllegalArgumentException("Error! Account must have a name to pay for group");
        }

        this.uid = UIDGenerator.generateId();
        this.group = group;
        this.account = account;
        this.addedByUser = addedByUser;

        this.activeDateTime = Instant.now();
        this.expireDateTime = DateTimeUtil.getVeryLongAwayInstant();
        this.status = PaidGroupStatus.ACTIVE;

    }

    private PaidGroup() {
        // For JPA
    }

    /*
    Getters and setters
    Some things should be immutable -- group, added user, active timestamp, so leaving out setters for them.
    */

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public Group getGroup() {
        return group;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) { this.account = account; } // we may want to reassign a group at some point

    public Instant getActiveDateTime() {
        return activeDateTime;
    }

    public User getAddedByUser() { return addedByUser; }

    public User getRemovedByUser() { return removedByUser; }

    public void setRemovedByUser(User removedByUser) { this.removedByUser = removedByUser; }

    public void setActiveDateTime(Instant activeDateTime) { this.activeDateTime = activeDateTime; }

    public Instant getExpireDateTime() {
        return expireDateTime;
    }

    public void setExpireDateTime(Instant expireDateTime) { this.expireDateTime = expireDateTime; }

    public PaidGroupStatus getStatus() { return status; }

    public void setStatus(PaidGroupStatus status) { this.status = status; }

    public boolean isActive() {
        return PaidGroupStatus.ACTIVE.equals(status);
    }

    public void suspend() {
        this.status = PaidGroupStatus.SUSPENDED;
        this.expireDateTime = Instant.now();
        this.group.setPaidFor(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PaidGroup)) {
            return false;
        }

        PaidGroup paidGroup = (PaidGroup) o;

        return getUid() != null ? getUid().equals(paidGroup.getUid()) : paidGroup.getUid() == null;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }
}
