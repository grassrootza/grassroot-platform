package za.org.grassroot.core.domain.media;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Task to hold a media file that we can attach to various things, but which we do not compress or analyze in any way
 * (e.g., for LiveWire alerts, PDF flyers, etc)
 */
@Entity @Getter
@Table(name = "media_file", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bucket", "key"}, name="uk_media_file_bucket_key")
})
public class MediaFileRecord implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Basic
    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name = "bucket", nullable = false)
    @Setter private String bucket;

    @Basic
    @Column(name = "key", nullable = false)
    @Setter private String key;

    @Basic
    @Column(name = "creation_time", nullable = false, updatable = false) // i.e., when the image was first stored
    private Instant creationTime;

    @Basic
    @Column(name = "stored_time")
    @Setter private Instant storedTime;

    @Basic
    @Column(name = "md5_hash", length = 24)
    @Setter private String md5;

    @Basic
    @Column(name = "mime_type")
    @Setter private String mimeType;

    @Version
    private Integer version;

    @Basic
    @Column(name = "read_count", nullable = false)
    private long readRequests;

    @Basic
    @Column(name = "file_name")
    @Setter private String fileName;

    @Transient
    @Setter private String createdByUserUid;

    @Transient
    @Setter private String createdByUserName;

    @Transient
    @Setter private String preSignedUrl; // for secure access

    private MediaFileRecord() {
        // for JPA
    }

    public MediaFileRecord(String bucket, String contentType, String key, String fileName, String createdByUserUid) {
        Objects.requireNonNull(bucket);
        this.uid = UIDGenerator.generateId();
        this.bucket = bucket;
        this.key = key == null ? uid : key;
        this.creationTime = Instant.now();
        this.readRequests = 0;
        this.mimeType = contentType;
        this.fileName = fileName;
        this.createdByUserUid = createdByUserUid;
    }

    public void incrementReadRequests() {
        this.readRequests++;
    }

    @Override
    public String toString() {
        return "MediaFileRecord{" +
                "bucket='" + bucket + '\'' +
                ", key='" + key + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", createdByUserName='" + createdByUserName + '\'' +
                '}';
    }
}
