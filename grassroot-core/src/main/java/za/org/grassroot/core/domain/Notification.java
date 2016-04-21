package za.org.grassroot.core.domain;


import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by paballo on 2016/04/06.
 */
@Entity
@Table(name ="notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name="creation_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne
    private User user;

    @ManyToOne
    private GcmRegistration gcmRegistration;

    @ManyToOne
    private EventLog eventLog;

    @ManyToOne
    private LogBookLog logBookLog;

    @Basic
    @Column(name ="read")
    private boolean read =false;

    @Basic
    @Column(name ="delivered")
    private boolean delivered =false;

    @Enumerated
    private UserMessagingPreference userMessagingPreference;

    @Enumerated
    private NotificationType notificationType;

    @Basic
    @Column(name = "message")
    private String message;

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = Instant.now();
        }
    }

    public Notification(){
    }

    public Notification(User user,EventLog eventLog,GcmRegistration gcmRegistration,Boolean read,
                        Boolean delivered, NotificationType notificationType, Instant createdDateTime){
        this.uid = UIDGenerator.generateId();
        this.user=user;
        this.eventLog=eventLog;
        this.gcmRegistration=gcmRegistration;
        this.read=read;
        this.delivered=delivered;
        this.createdDateTime=createdDateTime;
        this.notificationType = notificationType;
        this.userMessagingPreference = user.getMessagingPreference();
    }

    public Notification(User user,LogBookLog logBookLog,GcmRegistration gcmRegistration,Boolean read,
                        Boolean delivered, NotificationType notificationType, Instant createdDateTime){
        this.uid = UIDGenerator.generateId();
        this.user=user;
        this.logBookLog = logBookLog;
        this.gcmRegistration=gcmRegistration;
        this.read=read;
        this.delivered=delivered;
        this.createdDateTime=createdDateTime;
        this.notificationType = notificationType;
        this.userMessagingPreference = user.getMessagingPreference();
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public GcmRegistration getGcmRegistration() {
        return gcmRegistration;
    }

    public void setGcmRegistration(GcmRegistration gcmRegistration) {
        this.gcmRegistration = gcmRegistration;
    }



    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    public void setEventLog(EventLog eventLog) {
        this.eventLog = eventLog;
    }

    public LogBookLog getLogBookLog() {
        return logBookLog;
    }


    public void setLogBookLog(LogBookLog logBookLog) {
        this.logBookLog = logBookLog;
    }

    public UserMessagingPreference getUserMessagingPreference() {
        return userMessagingPreference;
    }

    public void setUserMessagingPreference(UserMessagingPreference userMessagingPreference) {
        this.userMessagingPreference = userMessagingPreference;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "uid='" + uid + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", user=" + user +
                ", gcmRegistration=" + gcmRegistration +
                '}';
    }
}
