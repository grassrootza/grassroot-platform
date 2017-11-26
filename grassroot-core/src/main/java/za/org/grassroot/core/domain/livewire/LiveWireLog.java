package za.org.grassroot.core.domain.livewire;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.LiveWireLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/05/16.
 * note: might also have handled this using inheritance, but class proliferation & boiler place probably
 * not worth it (plus can always refactor, as will keep one table regardless)
 */
@Entity
@Table(name = "live_wire_log")
public class LiveWireLog implements ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "alert_id", nullable = true)
    private LiveWireAlert alert;

    @ManyToOne
    @JoinColumn(name = "subscriber_id", nullable = true)
    private DataSubscriber subscriber;

    @ManyToOne
    @JoinColumn(name = "user_acting_id", nullable = true)
    private User userTakingAction;

    @ManyToOne
    @JoinColumn(name = "user_targeted_id", nullable = true)
    private User userTargeted;

    @Enumerated(EnumType.STRING)
    @Column(name="type", nullable = false, length = 50)
    private LiveWireLogType type;

    @Basic
    @Column(name="notes")
    private String notes;

    public static class Builder {
        private LiveWireAlert alert;
        private DataSubscriber subscriber;
        private User userTakingAction;
        private User userTargeted;
        private LiveWireLogType type;
        private String notes;

        public Builder alert(LiveWireAlert alert) {
            this.alert = alert;
            return this;
        }

        public Builder subscriber(DataSubscriber subscriber) {
            this.subscriber = subscriber;
            return this;
        }

        public Builder userTakingAction(User user) {
            this.userTakingAction = user;
            return this;
        }

        public Builder userTargeted(User user) {
            this.userTargeted = user;
            return this;
        }

        public Builder type(LiveWireLogType type) {
            this.type = type;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public LiveWireLog build() {
            Objects.requireNonNull(type);
            LiveWireLog log = new LiveWireLog(type);
            log.userTakingAction = userTakingAction;
            log.alert = alert;
            log.subscriber = subscriber;
            log.userTargeted = userTargeted;
            log.notes = notes;
            return log;
        }
    }

    private LiveWireLog() {
        // for JPA
    }

    private LiveWireLog(LiveWireLogType type) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.type = Objects.requireNonNull(type);
    }

    public String getUid() {
        return uid;
    }

    @Override
    public User getUser() {
        return userTakingAction;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public LiveWireAlert getAlert() {
        return alert;
    }

    public DataSubscriber getSubscriber() {
        return subscriber;
    }

    public User getUserTakingAction() {
        return userTakingAction;
    }

    public User getUserTargeted() {
        return userTargeted;
    }

    public LiveWireLogType getType() {
        return type;
    }

    public String getNotes() {
        return notes;
    }
}
