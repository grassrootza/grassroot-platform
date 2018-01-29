package za.org.grassroot.core.domain.media;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Task to hold a media file that we can attach to various things, but which we do not compress or analyze in any way
 * (e.g., for LiveWire alerts, PDF flyers, etc)
 */
@Entity
@Table(name = "media_file", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bucket", "key"}, name="uk_media_file_bucket_key")
})
public class MediaFileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Basic
    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name = "bucket", nullable = false)
    private String bucket;

    @Basic
    @Column(name = "key", nullable = false)
    private String key;

    @Basic
    @Column(name = "creation_time", nullable = false, updatable = false) // i.e., when the image was first stored
    private Instant creationTime;

    @Basic
    @Column(name = "stored_time")
    private Instant storedTime;

    @Basic
    @Column(name = "md5_hash", length = 24)
    private String md5;

    @Basic
    @Column(name = "mime_type")
    private String mimeType;

    @Version
    private Integer version;

    @Basic
    @Column(name = "read_count", nullable = false)
    private long readRequests;

    private MediaFileRecord() {
        // for JPA
    }

    public MediaFileRecord(String bucket, String contentType, String key) {
        Objects.requireNonNull(bucket);
        this.uid = UIDGenerator.generateId();
        this.bucket = bucket;
        this.key = key == null ? uid : key;
        this.creationTime = Instant.now();
        this.readRequests = 0;
        this.mimeType = contentType;
    }

    public String getUid() {
        return uid;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKey() {
        return key;
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

    public long getReadRequests() {
        return readRequests;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    public void setStoredTime(Instant storedTime) {
        this.storedTime = storedTime;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public void incrementReadRequests() {
        this.readRequests++;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
