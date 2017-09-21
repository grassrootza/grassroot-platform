package za.org.grassroot.core.domain;


import za.org.grassroot.core.enums.GroupLogType;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
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

    // used for whenever the log involves a third type of entity (e.g., an account, a sub-group, a target user)
    @Column(name = "user_or_sub_group_id")
    private Long auxiliaryId;

    @Column(name = "description")
    private String description;

    private GroupLog() {
        // for JPA
    }

    public GroupLog(Group group, User user, GroupLogType groupLogType, Long auxiliaryId) {
        this(group, user, groupLogType, auxiliaryId, null);
    }

    public GroupLog(Group group, User user, GroupLogType groupLogType, Long auxiliaryId, String description) {
        this.group = Objects.requireNonNull(group);
        this.user = user;
        this.groupLogType =  Objects.requireNonNull(groupLogType);
        this.auxiliaryId = auxiliaryId;
        this.description = description;
        this.createdDateTime = Instant.now();
    }

    public Long getId() {
        return id;
    }

    // nasty bit of legacy here
    public String getUid() { return null; }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public GroupLogType getGroupLogType() {
        return groupLogType;
    }

    public Long getAuxiliaryId() {
        return auxiliaryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "GroupLog{" +
                "id=" + id +
                ", groupLogType=" + groupLogType +
                ", auxiliaryId=" + auxiliaryId +
                ", description='" + description + '\'' +
                ", createdDateTime=" + createdDateTime +
                '}';
    }
}
