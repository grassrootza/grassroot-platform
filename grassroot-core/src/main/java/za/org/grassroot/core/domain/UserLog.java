package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.UserLogType;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Created by luke on 2016/02/22.
 */
@Entity
@Table(name="user_log",
        indexes = {@Index(name = "idx_user_log_user_id",  columnList="user_id", unique = false),
                @Index(name = "idx_user_log_userlogtype", columnList = "user_log_type", unique = false)})
public class UserLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Basic
    @Column(name="created_date_time", nullable = false, insertable = true, updatable = false)
    private Timestamp createdDateTime;

    @Basic
    @Column(name="user_log_type")
    private UserLogType userLogType;

    @Basic
    @Column(name="user_id")
    private Long userId;

    @Basic
    @Column(name="description", length = 255)
    private String description;

    public UserLog() {
    }

    public UserLog(Long userId, UserLogType userLogType) {
        this.userId = userId;
        this.userLogType = userLogType;
    }

    public UserLog(Long userId, UserLogType userLogType, String description) {
        this.userId = userId;
        this.userLogType = userLogType;
        this.description = description;
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Timestamp.valueOf(LocalDateTime.now());
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public UserLogType getUserLogType() {
        return userLogType;
    }

    public void setUserLogType(UserLogType userLogType) {
        this.userLogType = userLogType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "UserLog{" +
                "id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", userId=" + userId +
                ", userLogType=" + userLogType +
                ", description='" + description + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        int result = userId != null ? userId.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final UserLog userLog = (UserLog) o;

        if (id != null ? !id.equals(userLog.id) : userLog.id != null) { return false; }
        if (userId != null ? !userId.equals(userLog.userId) : userLog.userId != null) { return false; }
        if (createdDateTime != null ? !createdDateTime.equals(userLog.createdDateTime) : userLog.createdDateTime != null) { return false; }

        return true;

    }
}
