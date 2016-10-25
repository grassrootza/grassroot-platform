package za.org.grassroot.core.domain;

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

    @Column(name="user_uid", nullable = false)
    private String userUid;

    @Column(name="group_uid")
    private String groupUid;

    @Column(name="paid_group_uid")
    private String paidGroupUid;

    @Column(name="description", length = 255)
    private String description;

    @Column(name="reference_amount")
    private Long amountBilledOrPaid;

    private AccountLog() {
        // for JPA
    }

    public AccountLog(String userUid, Account account, AccountLogType accountLogType, String auxiliary) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(account);
        Objects.requireNonNull(accountLogType);

        this.uid = UIDGenerator.generateId();
        this.userUid = userUid;
        this.account = account;
        this.accountLogType = accountLogType;
        this.description = auxiliary;
        this.creationTime = Instant.now();
    }

    public AccountLog(String userUid, Account account, AccountLogType accountLogType, String groupUid,
                      String paidGroupUid, String description) {
        this(userUid, account, accountLogType, description);
        this.groupUid = groupUid;
        this.paidGroupUid = paidGroupUid;
    }

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Account getAccount() { return account; }

    public Instant getCreationTime() {
        return creationTime;
    }

    public AccountLogType getAccountLogType() {
        return accountLogType;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getGroupUid() { return groupUid; }

    public String getPaidGroupUid() { return paidGroupUid; }

    public String getDescription() {
        return description;
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
                ", userUid=" + userUid +
                ", groupUid=" + groupUid +
                ", accountLogType=" + accountLogType +
                ", description='" + description + '\'' +
                '}';
    }
}
