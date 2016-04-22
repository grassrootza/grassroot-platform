package za.org.grassroot.core.domain;


import za.org.grassroot.core.enums.GroupLogType;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Date;

@Entity
@Table(name = "group_log",
        indexes = {@Index(name = "idx_group_log_group_id",  columnList="group_id", unique = false),
        @Index(name = "idx_group_log_grouplogtype", columnList = "group_log_type", unique = false)})
public class GroupLog implements Serializable, ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @Column(name="group_id", nullable = false)
    private Group group;

    @Column(name="user_id", nullable = false)
    private User user;

    @Column(name="group_log_type", nullable = false)
    private GroupLogType groupLogType;

    @Column
    private Long userOrSubGroupId;

    @Column
    private String description;

    private GroupLog() {
        // for JPA
    }

    public GroupLog(Group group, User user, GroupLogType groupLogType, Long userOrSubGroupId, String description) {
        this.group = group;
        this.user = user;
        this.groupLogType = groupLogType;
        this.userOrSubGroupId = userOrSubGroupId;
        this.description = description;
    }

    public GroupLog(Group group, User user, GroupLogType groupLogType, Long userOrSubGroupId) {
        this(group, user, groupLogType, userOrSubGroupId, null);
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
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

    public void setGroupLogType(GroupLogType groupLogType) {
        this.groupLogType = groupLogType;
    }

    public Long getUserOrSubGroupId() {
        return userOrSubGroupId;
    }

    public void setUserOrSubGroupId(Long userOrSubGroupId) {
        this.userOrSubGroupId = userOrSubGroupId;
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
                ", createdDateTime=" + createdDateTime +
                ", groupLogType=" + groupLogType +
                ", userOrSubGroupId=" + userOrSubGroupId +
                ", description='" + description + '\'' +
                '}';
    }
}
