package za.org.grassroot.core.domain.media;

import za.org.grassroot.core.enums.ActionLogType;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/02/23.
 * Entity to store information about an image that has been stored externally, processed (e.g., with a number of people identified), etc
 * Other option could have been to add some of this to ActionLog, but we may use it in GroupLog, TodoLog, etc., so this seems better
 * as something normalized, and keep it within this table (also EventLog etc are among our largest tables, so messing with them is
 * going to be high risk / likely low return).
 *
 * Note: Strictly speaking, it would be better to just have an ActionLog entity in here, rather than the UID and Type.
 * _But_ that would require some big changes to the ActionLog ORM (introducing mapped super class, etc), or a bit of JPA
 * index ninja-ing that is beyond my pay grade. Also we will rarely need the whole entity, since it's the action log UID
 * that we use as the key in external storage, i.e., which is the point. So for now am doing it this way.
 *
 * Note: since each image should link to a single event log (for tracing, and much else)
 */
@Entity
@Table(name = "image_record", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"action_log_type", "action_log_uid"}, name = "uk_image_action_log_uid_type")
})
public class ImageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name="action_log_type", nullable = false, length = 50)
    private ActionLogType actionLogType;

    @Column(name = "action_log_uid", nullable = false)
    private String actionLogUid;

    @Column(name = "bucket", nullable = false)
    private String bucket;

    @Column(name = "creation_time", nullable = false, updatable = false) // i.e., when the image was first stored
    private Instant creationTime;

    @Column(name = "stored_time")
    private Instant storedTime;

    @Column(name = "md5_hash", length = 24)
    private String md5;

    @Column(name = "analyzed")
    private boolean analyzed;

    @Column(name = "number_faces", nullable = true)
    private Integer analyzedFaces;

    @Column(name = "count_modified")
    private boolean countModified;

    @Column(name = "revised_faces", nullable = true)
    private Integer revisedFaces;

    // may need a bit more of this later, but for now, a place holder for, e.g., recording how many people included
    @Column(name = "auxiliary", length = 255)
    private String auxiliaryText;

    public static class Builder {
        private ActionLogType actionLogType;
        private String actionLogUid;
        private String bucket;
        private String md5;
        private String auxiliaryText;

        public Builder actionLogType(ActionLogType actionLogType) {
            this.actionLogType = actionLogType;
            return this;
        }

        public Builder actionLogUid(String actionLogUid) {
            this.actionLogUid = actionLogUid;
            return this;
        }

        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        public Builder md5(String md5) {
            this.md5 = md5;
            return this;
        }

        public Builder auxText(String auxiliaryText) {
            this.auxiliaryText = auxiliaryText;
            return this;
        }

        public ImageRecord build() {
            Objects.requireNonNull(actionLogType);
            Objects.requireNonNull(actionLogUid);
            Objects.requireNonNull(bucket);
            Objects.requireNonNull(md5);

            ImageRecord record = new ImageRecord(actionLogType, actionLogUid);
            record.bucket = bucket;
            record.md5 = md5;
            record.auxiliaryText = auxiliaryText;
            return record;
        }

    }

    private ImageRecord() {
        // for JPA
    }

    private ImageRecord(ActionLogType actionLogType, String actionLogUid) {
        this.actionLogType = actionLogType;
        this.actionLogUid = actionLogUid;
        this.creationTime = Instant.now();
        this.analyzed = false;
    }

    public void setStoredTime(Instant storedTime) {
        this.storedTime = storedTime;
    }

    public ActionLogType getActionLogType() {
        return actionLogType;
    }

    public String getActionLogUid() {
        return actionLogUid;
    }

    public String getBucket() {
        return bucket;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public Instant getStoredTime() {
        return storedTime;
    }

    public String getMd5() {
        return md5;
    }

    public String getAuxiliaryText() {
        return auxiliaryText;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public Integer getAnalyzedFaces() {
        return analyzedFaces;
    }

    public void setAnalyzedFaces(Integer analyzedFaces) {
        this.analyzedFaces = analyzedFaces;
    }

    public boolean isCountModified() {
        return countModified;
    }

    public Integer getRevisedFaces() {
        return revisedFaces;
    }

    public void setCountModified(boolean countModified) {
        this.countModified = countModified;
    }

    public void setRevisedFaces(Integer revisedFaces) {
        this.revisedFaces = revisedFaces;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ImageRecord that = (ImageRecord) o;

        if (actionLogType != that.actionLogType) return false;
        if (!actionLogUid.equals(that.actionLogUid)) return false;
        return creationTime.equals(that.creationTime);
    }

    @Override
    public int hashCode() {
        int result = actionLogType.hashCode();
        result = 31 * result + actionLogUid.hashCode();
        result = 31 * result + creationTime.hashCode();
        return result;
    }
}
