package za.org.grassroot.core.domain.livewire;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/05/07.
 * Creating a request entity to handle partials would be heavy compared to frequency of use case
 * (different to events in this regard), hence using booleans to flag
 */
@Entity
@Table(name = "live_wire_alert")
public class LiveWireAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Basic
    @Column(name = "uid", unique = true, nullable = false, updatable = false)
    private String uid;

    @Column(name = "creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User creatingUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private LiveWireAlertType type;

    @ManyToOne
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @ManyToOne
    @JoinColumn(name = "contact_user_id")
    private User contactUser;

    // since can over-ride the display name used in Grassroot (if non null)
    @Basic
    @Column(name = "contact_name")
    private String contactName;

    @Basic
    @Column(name = "description")
    private String description;

    @Basic
    @Column(name = "send_time")
    private Instant sendTime;

    @Basic
    @Column(name = "sent")
    private boolean sent;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column = @Column(nullable = true)),
            @AttributeOverride(name="longitude", column = @Column(nullable = true))
    })
    private GeoLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", length = 50, nullable = true)
    private LocationSource locationSource;

    public static class Builder {
        private User creatingUser;
        private LiveWireAlertType type;
        private User contactUser;
        private String contactName;
        private Meeting meeting;
        private Group group;
        private String description;
        private Instant sendTime;

        public Builder creatingUser(User creatingUser) {
            this.creatingUser = creatingUser;
            return this;
        }

        public Builder type(LiveWireAlertType type) {
            this.type = type;
            return this;
        }

        public Builder meeting(Meeting meeting) {
            this.meeting = meeting;
            return this;
        }

        public Builder group(Group group) {
            this.group = group;
            return this;
        }

        public Builder contactUser(User user) {
            this.contactUser = user;
            return this;
        }

        public Builder contactName(String contactName) {
            this.contactName = contactName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder sendTime(Instant sendTime) {
            this.sendTime = sendTime;
            return this;
        }

        public LiveWireAlert build() {
            LiveWireAlert alert = new LiveWireAlert(
                    Objects.requireNonNull(creatingUser),
                    Objects.requireNonNull(type),
                    meeting, group, description);

            if (sendTime != null) {
                alert.setSendTime(sendTime);
            }

            if (contactUser != null) {
                alert.setContactUser(contactUser);
                alert.setContactName(contactName);
            }

            return alert;
        }
    }

    private LiveWireAlert() {
        // for JPA
    }

    private LiveWireAlert(User creatingUser, LiveWireAlertType type, Meeting meeting, Group group, String description) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.creatingUser = creatingUser;
        this.type = type;
        this.meeting = meeting;
        this.group = group;
        this.description = description;
        this.sent = false;
    }

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public User getCreatingUser() {
        return creatingUser;
    }

    public LiveWireAlertType getType() {
        return type;
    }

    public User getContactUser() {
        return contactUser;
    }

    public String getContactName() {
        return contactName;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public Group getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public Instant getSendTime() {
        return sendTime;
    }

    public boolean isSent() {
        return sent;
    }

    public void setType(LiveWireAlertType type) {
        this.type = type;
    }

    public void setMeeting(Meeting meeting) {
        this.meeting = meeting;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public void setContactUser(User contactUser) {
        this.contactUser = contactUser;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSendTime(Instant sendTime) {
        this.sendTime = sendTime;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public GeoLocation getLocation() {
        return location;
    }

    public void setLocation(GeoLocation location) {
        this.location = location;
    }

    public LocationSource getLocationSource() {
        return locationSource;
    }

    public void setLocationSource(LocationSource locationSource) {
        this.locationSource = locationSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LiveWireAlert alert = (LiveWireAlert) o;

        return uid.equals(alert.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }

    @Override
    public String toString() {
        return "LiveWireAlert{" +
                "type=" + type +
                ", meeting=" + meeting +
                ", group=" + group +
                ", description='" + description + '\'' +
                ", sendTime=" + sendTime +
                ", sent=" + sent +
                '}';
    }
}
