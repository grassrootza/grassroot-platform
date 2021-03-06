package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.media.MediaFileRecord;

import java.util.Set;

public interface MediaFileRecordRepository extends JpaRepository<MediaFileRecord, Long> {

    MediaFileRecord findOneByUid(String uid);

    Set<MediaFileRecord> findByUidIn(Set<String> uids);

    MediaFileRecord findByBucketAndKey(String bucket, String key);

    Set<MediaFileRecord> findByBucketAndKeyIn(String bucket, Set<String> keys);
}