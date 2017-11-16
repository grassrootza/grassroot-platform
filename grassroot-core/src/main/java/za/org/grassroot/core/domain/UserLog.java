package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2016/02/22.
 */
@Entity
@Table(name="user_log",
        uniqueConstraints = {@UniqueConstraint(name = "uk_user_log_request_uid", columnNames = "uid")})
public class UserLog implements ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @Enumerated(EnumType.STRING)
    @Column(name="user_log_type", nullable = false, length = 50)
    private UserLogType userLogType;

    @Column(name="user_uid", nullable = false)
    private String userUid;

    @Column(name="description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name="user_interface", nullable = false, length = 50)
    private UserInterfaceType userInterface;

    private UserLog() {
        // for JPA
    }

    public UserLog(String userUid, UserLogType userLogType, String description, UserInterfaceType userInterfaceType) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.userUid = userUid;
        this.userLogType = Objects.requireNonNull(userLogType);
        this.description = description;
        this.userInterface = userInterfaceType;
    }

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    @Override
    public User getUser() {
        // todo : return this user (cleanse old technical debt on user uid field)
        return null;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Instant creationTime) { this.creationTime = creationTime; }

    public UserLogType getUserLogType() {
        return userLogType;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UserInterfaceType getUserInterface() { return userInterface; }

    @Override
    public int hashCode() {
        return (getUid() != null) ? getUid().hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final UserLog that = (UserLog) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;

    }

    @Override
    public String toString() {
        return "UserLog{" +
                "id=" + id +
                ", userLogType=" + userLogType +
                ", userUid=" + userUid +
                ", description='" + description + '\'' +
                ", creationTime =" + creationTime +
                '}';
    }
}
