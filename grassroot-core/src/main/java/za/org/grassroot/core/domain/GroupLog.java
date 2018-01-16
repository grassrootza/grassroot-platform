package za.org.grassroot.core.domain;


import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.enums.GroupLogType;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity @Getter
@Table(name = "group_log",
        indexes = {@Index(name = "idx_group_log_group_id",  columnList="group_id", unique = false),
        @Index(name = "idx_group_log_grouplogtype", columnList = "group_log_type", unique = false)})
public class GroupLog implements Serializable, ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name="created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    @ManyToOne
    @JoinColumn(name="group_id", nullable = false)
    private Group group;

    @ManyToOne
    @JoinColumn(name="user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name="group_log_type", nullable = false, length = 50)
    private GroupLogType groupLogType;

    @ManyToOne
    @JoinColumn(name="target_user_id")
    private User targetUser;

    @ManyToOne
    @JoinColumn(name="target_group_id")
    private Group targetGroup;

    @ManyToOne
    @JoinColumn(name="target_account_id")
    private Account targetAccount;

    @ManyToOne
    @JoinColumn(name="broadcast_id")
    @Setter private Broadcast broadcast;

    @Column(name = "description")
    private String description;

    private GroupLog() {
        // for JPA
    }

    public GroupLog(Group group, User user, GroupLogType type,
                    User targetUser, Group targetGroup, Account targetAccount, String description) {
        this(group, user, type, description);
        validateLogTypeNullTargets(type, targetUser, targetGroup, targetAccount);
        this.targetUser = targetUser;
        this.targetGroup = targetGroup;
        this.targetAccount = targetAccount;
    }

    public GroupLog (Group group, User user, GroupLogType type, String description) {
        validateLogTypeNullTargets(groupLogType, null, null, null);
        this.createdDateTime = Instant.now();
        this.group = Objects.requireNonNull(group);
        this.user = user;
        this.groupLogType = Objects.requireNonNull(type);
        this.description = description;
    }

    public Instant getCreationTime() {
        return createdDateTime;
    }

    private void validateLogTypeNullTargets(GroupLogType type, User targetUser, Group targetSubGroup,
                                            Account targetAccount) {
        if (GroupLogType.targetUserChangeTypes.contains(type)) {
            Objects.requireNonNull(targetUser);
        }
        if (GroupLogType.targetGroupTypes.contains(type)) {
            Objects.requireNonNull(targetSubGroup);
        }
        if (GroupLogType.accountTypes.contains(type)) {
            Objects.requireNonNull(targetAccount);
        }
    }

    // nasty bit of legacy here
    public String getUid() { return null; }

    // just in case legacy fix up didn't work
    public boolean hasTargetUser() {
        return targetUser != null;
    }

    public GrassrootEntity getTarget() {
        return targetUser != null ? targetUser :
                targetGroup != null ? targetGroup : targetAccount;
    }

    public String getUserNameSafe() {
        return targetUser != null ? targetUser.getName() : user.getName();
    }

    @Override
    public String toString() {
        return "GroupLog{" +
                "id=" + id +
                ", groupLogType=" + groupLogType +
                ", targetEntityId=" + (getTarget() != null ? getTarget().getName() : "null") +
                ", description='" + description + '\'' +
                ", createdDateTime=" + createdDateTime +
                '}';
    }
}
