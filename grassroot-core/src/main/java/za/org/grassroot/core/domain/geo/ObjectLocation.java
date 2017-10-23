package za.org.grassroot.core.domain.geo;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Meeting;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import java.time.Instant;

@Embeddable
@Getter @Setter
public class ObjectLocation {

    @Column(name = "uid", nullable = false)
    private String uid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "score", nullable = false)
    private float score;

    @Column(name = "type")
    private String type;

    @Column(name = "url")
    private String url;

    @Column(name = "description")
    private String description;

    @Column(name = "canAccess")
    private boolean canAccess;

    @Transient
    private Instant createdDateTime;

    @Transient
    private String locationDescription;

    private int groupSize;

    private int groupTasks;

    private ObjectLocation () {
        // for JPA
    }

    private ObjectLocation (String uid, String type, boolean isPublic) {
        this.uid = uid;
        this.type = type;
        this.canAccess = isPublic;
        String access = ""; // (isPublic ? "public/" : "");
        if (JpaEntityType.GROUP.toString().equals(type))
            this.url = "/group/" + access + "view?groupUid=" + uid;
        else
            this.url = "/meeting/" + access + "view?eventUid=" + uid;
    }

    public ObjectLocation (Meeting meeting, MeetingLocation meetingLocation) {
        this(meeting.getUid(),
                meeting.getName(), meetingLocation.getLocation().getLatitude(),
                meetingLocation.getLocation().getLongitude(), meetingLocation.getScore(),
                "MEETING",
                "Where: " + meeting.getEventLocation() +
                "Date and Time: " + meeting.getEventStartDateTime() +
                "Creation Date: " + meeting.getCreatedDateTime(),
                meeting.isPublic(),
                meeting.getAncestorGroup().getMemberships().size(),
                (meeting.getAncestorGroup().getDescendantEvents().size() + meeting.getAncestorGroup().getDescendantTodos().size()));
        this.locationDescription = meeting.getEventLocation();
    }

    public ObjectLocation(Group group,GroupLocation groupLocation){
        this(group.getUid(),
                group.getName(),groupLocation.getLocation().getLatitude(),
                groupLocation.getLocation().getLongitude(),groupLocation.getScore(),"GROUP",group.isDiscoverable());
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type, boolean isPublic) {
        this(uid, name, latitude, longitude, score, type, isPublic, 0, 0);
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type,
                           String description, boolean isPublic) {
        this(uid, name, latitude, longitude, score, type, description, isPublic, 0, 0);
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type, String description, boolean isPublic, Group group) {
        this(uid, name, latitude, longitude, score, type, description, isPublic, group.getMemberships().size(), (group.getDescendantEvents().size() + group.getDescendantTodos().size()));
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type, boolean isPublic,
                           int groupSize, int groupTasks) {
        this(uid, name, latitude, longitude, score, type, "", isPublic,  groupSize, groupTasks);
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type, String description, boolean isPublic,
                           int groupSize, int groupTasks) {
        this(uid, type, isPublic);
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.score = score;
        this.type = type;
        this.description = description;
        this.groupSize = groupSize;
        this.groupTasks = groupTasks;
    }

    @Override
    public String toString () {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("latitude=").append(latitude);
        sb.append(", longitude=").append(longitude);
        sb.append(", uid=").append(uid);
        sb.append(", name=").append(name);
        sb.append(", score=").append(score);
        sb.append(", type=").append(type);
        sb.append(", canAccess=").append(canAccess);
        sb.append(", url='").append(url).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", groupSize=").append(groupSize);
        sb.append(", groupTasks=").append(groupTasks);
        sb.append('}');
        return sb.toString();
    }
}
