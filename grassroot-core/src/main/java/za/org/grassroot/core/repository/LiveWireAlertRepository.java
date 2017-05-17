package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2017/05/07.
 */
public interface LiveWireAlertRepository extends JpaRepository<LiveWireAlert, Long> {

    LiveWireAlert findOneByUid(String alertUid);

    List<LiveWireAlert> findBySendTimeBetweenAndSentFalse(Instant start, Instant end);

    Page<LiveWireAlert> findByCompleteTrue(Pageable pageable);

    Page<LiveWireAlert> findByCompleteTrueAndReviewedFalse(Pageable pageable);

    long countByCreationTimeBetween(Instant start, Instant end);

}
