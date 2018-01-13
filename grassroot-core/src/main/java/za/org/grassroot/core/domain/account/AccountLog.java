package za.org.grassroot.core.domain.account;

import lombok.Getter;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2016/04/03.
 */
@Entity
@Table(name="account_log",
        uniqueConstraints = {@UniqueConstraint(name = "uk_account_log_uid", columnNames = "uid")})
@Getter
public class AccountLog implements ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    private Account account;

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @Enumerated(EnumType.STRING)
    @Column(name="account_log_type", nullable = false, length = 50)
    private AccountLogType accountLogType;

    @ManyToOne
    @JoinColumn(name = "user_uid", referencedColumnName = "uid")
    private User user;

    @ManyToOne
    @JoinColumn(name = "group_uid", referencedColumnName = "uid")
    private Group group;

    @Column(name="paid_group_uid")
    private String paidGroupUid;

    @Column(name="description", length = 255)
    private String description;

    @Column(name="reference_amount")
    private Long amountBilledOrPaid;

    @ManyToOne
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    @Override
    public User getUser() {
        return null;
    }

    public static class Builder {
        private Account account;
        private User user;
        private AccountLogType accountLogType;
        private Group group;
        private Broadcast broadcast;
        private String paidGroupUid;
        private String description;
        private Long amountBilledOrPaid;

        public Builder(Account account) {
            this.account = account;
        }

        public Builder user(User user) {
            this.user = user;
            return this;
        }

        public Builder accountLogType(AccountLogType accountLogType) {
            this.accountLogType = accountLogType;
            return this;
        }

        public Builder group(Group group) {
            this.group = group;
            return this;
        }

        public Builder paidGroupUid(String paidGroupUid) {
            this.paidGroupUid = paidGroupUid;
            return this;
        }

        public Builder broadcast(Broadcast broadcast) {
            this.broadcast = broadcast;
            return this;
        }

        public Builder description(String description) {
            this.description = description.substring(Math.min(255, description.length()));
            return this;
        }

        public Builder billedOrPaid(Long amountBilledOrPaid) {
            this.amountBilledOrPaid = amountBilledOrPaid;
            return this;
        }

        public AccountLog build() {
            Objects.requireNonNull(account);
            Objects.requireNonNull(accountLogType);

            AccountLog accountLog = new AccountLog(account, user, accountLogType);
            accountLog.description = description;
            accountLog.group = group;
            accountLog.broadcast = broadcast;
            accountLog.paidGroupUid = paidGroupUid;
            accountLog.amountBilledOrPaid = amountBilledOrPaid;

            return accountLog;
        }
    }

    private AccountLog() {
        // for JPA
    }

    private AccountLog(Account account, User user, AccountLogType accountLogType) {
        this.uid = UIDGenerator.generateId();
        this.account = account;
        this.user = user;
        this.accountLogType = accountLogType;
        this.creationTime = Instant.now();
    }

    @Override
    public int hashCode() {
        return (getUid() != null) ? getUid().hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final AccountLog that = (AccountLog) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;
    }

    @Override
    public String toString() {
        return "AccountLog{" +
                "id=" + id +
                ", creationTime =" + creationTime +
                ", userUid=" + (user == null ? "null" : user.getUid()) +
                ", groupUid=" + group.getUid() +
                ", accountLogType=" + accountLogType +
                ", description='" + description + '\'' +
                '}';
    }
}
