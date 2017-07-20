package za.org.grassroot.core.domain.livewire;

import org.hibernate.annotations.Type;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    @Column(name = "complete")
    private boolean complete;

    @Basic
    @Column(name = "reviewed")
    private boolean reviewed;

    @ManyToOne
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;

    @Basic
    @Column(name = "sent")
    private boolean sent;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", length = 50)
    private LiveWireAlertDestType destinationType;

    // this is the private list of the user, if they have one
    @ManyToOne
    @JoinColumn(name = "subscriber_id")
    private DataSubscriber targetSubscriber;

    // these are the public lists selected by reviewer
    @Column(name = "public_list_uids")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] publicLists;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column = @Column(nullable = true)),
            @AttributeOverride(name="longitude", column = @Column(nullable = true))
    })
    private GeoLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", length = 50, nullable = true)
    private LocationSource locationSource;

    @Version
    private Integer version;

    public static class Builder {
        private User creatingUser;
        private LiveWireAlertType type;
        private User contactUser;
        private String contactName;
        private Meeting meeting;
        private Group group;
        private String description;
        private Instant sendTime;
        private boolean complete;
        private LiveWireAlertDestType destType;

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

        public Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        public Builder destType(LiveWireAlertDestType destType) {
            this.destType = destType;
            return this;
        }

        public LiveWireAlert build() {
            LiveWireAlert alert = new LiveWireAlert(
                    Objects.requireNonNull(creatingUser),
                    Objects.requireNonNull(type),
                    Objects.requireNonNull(destType),
                    meeting, group, description);

            if (sendTime != null) {
                alert.setSendTime(sendTime);
            }

            if (contactUser != null) {
                alert.setContactUser(contactUser);
                alert.setContactName(contactName);
            }

            alert.setComplete(complete);

            return alert;
        }
    }

    private LiveWireAlert() {
        // for JPA
    }

    private LiveWireAlert(User creatingUser, LiveWireAlertType type, LiveWireAlertDestType destType,
                          Meeting meeting, Group group, String description) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.creatingUser = creatingUser;
        this.type = type;
        this.meeting = meeting;
        this.group = group;
        this.description = description;
        this.complete = false;
        this.sent = false;
        this.tags = new String[0];
        this.publicLists = new String[0];
        this.destinationType = destType;
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

    public String getContactNumberFormatted() {
        return PhoneNumberUtil.invertPhoneNumber(contactUser.getPhoneNumber());
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactNameNullSafe() {
        return StringUtils.isEmpty(contactName) ? contactUser.getName() : contactName;
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

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public boolean isInstant() {
        return LiveWireAlertType.INSTANT.equals(type);
    }

    public User getReviewedByUser() {
        return reviewedByUser;
    }

    public void setReviewedByUser(User reviewedByUser) {
        this.reviewedByUser = reviewedByUser;
    }

    public String[] getTags() {
        return tags;
    }

    public List<String> getTagList() {
        return tags == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(tags));
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public void addTags(List<String> tagsToAdd) {
        List<String> currentTags = new ArrayList<>(StringArrayUtil.arrayToList(tags));
        currentTags.addAll(tagsToAdd);
        tags = StringArrayUtil.listToArrayRemoveDuplicates(currentTags);
    }

    public void reviseTags(List<String> tags) {
        Objects.requireNonNull(tags);
        this.tags = StringArrayUtil.listToArrayRemoveDuplicates(tags);
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

    public Integer getVersion() {
        return version;
    }

    public LiveWireAlertDestType getDestinationType() {
        return destinationType;
    }

    public void setDestinationType(LiveWireAlertDestType destinationType) {
        this.destinationType = destinationType;
    }

    public DataSubscriber getTargetSubscriber() {
        return targetSubscriber;
    }

    public void setTargetSubscriber(DataSubscriber targetSubscriber) {
        this.targetSubscriber = targetSubscriber;
    }

    public String[] getPublicLists() {
        return publicLists;
    }

    public List<String> getPublicListUids() {
        return StringArrayUtil.arrayToList(publicLists);
    }

    public void setPublicLists(String[] publicLists) {
        this.publicLists = publicLists;
    }

    public void setPublicListUids(List<String> uids) {
        this.publicLists = StringArrayUtil.listToArray(uids);
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
