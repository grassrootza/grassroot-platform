package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.media.ImageRecord;

/**
 * Created by luke on 2017/02/23.
 */
public interface ImageRecordRepository extends JpaRepository<ImageRecord, Long>, JpaSpecificationExecutor<ImageRecord> {

}
