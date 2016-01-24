package za.org.grassroot.core.domain;


import za.org.grassroot.core.enums.GroupLogType;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "group_log",
        indexes = {@Index(name = "idx_group_log_group_id",  columnList="group_id", unique = false),
        @Index(name = "idx_group_log_grouplogtype", columnList = "group_log_type", unique = false)})
public class GroupLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Date createdDateTime;
    @Basic
    @Column(name="group_id")
    private Long groupId;

    @Basic
    @Column(name="user_id")
    private Long userId;

    @Basic
    @Column(name="group_log_type")
    private GroupLogType groupLogType;

    @Basic
    @Column
    private Long userOrSubGroupId;

    @Basic
    @Column
    private String description;

    public GroupLog() {
    }

    public GroupLog(Long groupId, Long userId, GroupLogType groupLogType, Long userOrSubGroupId) {
        this.groupId = groupId;
        this.userId = userId;
        this.groupLogType = groupLogType;
        this.userOrSubGroupId = userOrSubGroupId;
    }

    public GroupLog(Long groupId, Long userId, GroupLogType groupLogType, Long userOrSubGroupId, String description) {
        this.groupId = groupId;
        this.userId = userId;
        this.groupLogType = groupLogType;
        this.userOrSubGroupId = userOrSubGroupId;
        this.description = description;
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Date();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Date getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Date createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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
                ", groupId=" + groupId +
                ", userId=" + userId +
                ", groupLogType=" + groupLogType +
                ", userOrSubGroupId=" + userOrSubGroupId +
                ", description='" + description + '\'' +
                '}';
    }
}
