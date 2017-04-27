package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.domain.JpaEntityType;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
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

    public String getUid () {
        return uid;
    }

    public void setUid (String uid) {
        this.uid = uid;
    }

    public String getName () {
        return name;
    }

    public void setName (String name) {
        this.name = name;
    }

    public void setLatitude (double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude (double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude () {
        return latitude;
    }

    public double getLongitude () {
        return longitude;
    }

    public float getScore () {
        return score;
    }

    public void setScore (float score) {
        this.score = score;
    }

    public String getType () {
        return type;
    }

    public void setType (String type) {
        this.type = type;
    }

    public String getUrl () {
        return url;
    }

    public void setUrl (String url) {
        this.url = url;
    }

    public String getDescription () {
        return description;
    }

    public void setDescription (String description) {
        this.description = description;
    }

    private ObjectLocation () {
        // for JPA
    }

    private ObjectLocation (String uid, String type) {
        this.uid = uid;
        this.type = type;
        if (JpaEntityType.GROUP.toString().equals(type))
            this.url = "/group/public/view?groupUid=" + uid;
        else
            this.url = "/meeting/public/view?eventUid=" + uid;
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type) {
        this(uid, type);
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.score = score;
        this.description = "";
    }

    public ObjectLocation (String uid, String name, double latitude, double longitude, float score, String type, String description) {
        this(uid, type);
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.score = score;
        this.type = type;
        this.description = description;
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
        sb.append(", url='").append(url).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
