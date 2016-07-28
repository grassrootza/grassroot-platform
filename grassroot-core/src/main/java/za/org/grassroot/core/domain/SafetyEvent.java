package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.*;
import java.time.temporal.ChronoUnit;

/**
 * Created by paballo on 2016/07/18.
 */
@Entity
@Table(name = "safety_event")
public class SafetyEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;


    @Column(name = "created_date_time", nullable = false, insertable = true, updatable = false)
    private Instant createdDateTime;

    @Column(name = "scheduled_reminder_time")
    private Instant scheduledReminderTime;

    @ManyToOne()
    @JoinColumn(name = "activated_by_user", nullable = false, updatable = false)
    private User activatedBy;

    @ManyToOne()
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @Column(name = "active")
    private boolean active;

    @Column(name = "false_alarm")
    private boolean falseAlarm;

    @Column(name = "responded_to")
    private boolean respondedTo;


    private SafetyEvent() {
    }

    public SafetyEvent(User activatedBy, Group parentGroup) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.activatedBy = activatedBy;
        this.group = parentGroup;
        this.active =true;
        //send a reminder after 20 minutes
        this.scheduledReminderTime = createdDateTime.plus(20, ChronoUnit.MINUTES);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public User getActivatedBy() {
        return activatedBy;
    }

    public void setActivatedBy(User activatedBy) {
        this.activatedBy = activatedBy;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }


    public void updateScheduledReminderTime() {
        this.scheduledReminderTime = scheduledReminderTime.plus(20, ChronoUnit.MINUTES);
    }


    public Instant getScheduledReminderTime() {
        return scheduledReminderTime;
    }

    public void setScheduledReminderTime(Instant scheduledReminderTime) {
        this.scheduledReminderTime = scheduledReminderTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isFalseAlarm() {
        return falseAlarm;
    }

    public void setFalseAlarm(boolean falseAlarm) {
        this.falseAlarm = falseAlarm;
    }

    public boolean isRespondedTo() {
        return respondedTo;
    }

    public void setRespondedTo(boolean respondedTo) {
        this.respondedTo = respondedTo;
    }


    @Override
    public String toString() {
        return "SafetyEvent{" +
                "id=" + id +
                ", uid='" + uid + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", group=" + group +
                '}';
    }
}
