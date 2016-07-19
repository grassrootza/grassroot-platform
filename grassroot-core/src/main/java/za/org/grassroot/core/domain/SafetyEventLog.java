package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.SafetyEventLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/07/19.
 */


@Entity
@Table(name="safety_event_log")
public class SafetyEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name="created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    @ManyToOne
    @JoinColumn(name = "safety_event_id", nullable = false, insertable = true)
    private SafetyEvent safetyEvent;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, insertable = true)
    private User user;


    @Column(name="false-alarm", nullable = false)
    private String validity;

    @Column(name ="responded")
    private boolean responded;

    @Enumerated(EnumType.STRING)
    @Column(name="event_log_type", nullable = false, length = 50)
    private SafetyEventLogType safetyEventLogType;


    private SafetyEventLog(){

    }

    public SafetyEventLog(User user, SafetyEvent safetyEvent, SafetyEventLogType safetyEventLogType, boolean responded, String validity){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.safetyEvent = safetyEvent;
        this.responded = responded;
        this.validity =validity;
        this.safetyEventLogType = safetyEventLogType;
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

    public SafetyEvent getSafetyEvent() {
        return safetyEvent;
    }

    public void setSafetyEvent(SafetyEvent safetyEvent) {
        this.safetyEvent = safetyEvent;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String valid() {
        return validity;
    }

    public void setValidity(String validity) {
        this.validity = validity;
    }

    public boolean isResponded() {
        return responded;
    }

    public void setResponded(boolean responded) {
        this.responded = responded;
    }

    public SafetyEventLogType getSafetyEventLogType() {
        return safetyEventLogType;
    }

    public void setSafetyEventLogType(SafetyEventLogType safetyEventLogType) {
        this.safetyEventLogType = safetyEventLogType;
    }
}
