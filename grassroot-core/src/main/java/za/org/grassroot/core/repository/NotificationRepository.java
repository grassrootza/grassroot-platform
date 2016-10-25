package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationRepository extends JpaRepository<Notification,Long>, JpaSpecificationExecutor<Notification> {

    Notification findByUid(String uid);

    List<Notification> findByUidIn(Set<String> uids);

    List<Notification> findByTargetAndForAndroidTimelineTrueOrderByCreatedDateTimeDesc(User target);

    Page<Notification> findByTargetAndForAndroidTimelineTrueOrderByCreatedDateTimeDesc(User target, Pageable pageable);

    List<Notification> findByTargetAndForAndroidTimelineTrueAndCreatedDateTimeGreaterThanOrderByCreatedDateTimeDesc(User target, Instant start);

    int countByTargetAndViewedOnAndroidFalseAndForAndroidTimelineTrue(User target);

    @Transactional(readOnly = true)
    List<Notification> findFirst75ByNextAttemptTimeBeforeOrderByNextAttemptTimeAsc(Instant time);

    @Transactional(readOnly = true)
    List<Notification> findFirst100ByReadFalseAndAttemptCountGreaterThanAndLastAttemptTimeGreaterThan(int minAttemptCount, Instant lastAttemptTime);
}
