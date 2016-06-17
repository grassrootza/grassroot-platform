package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationRepository extends JpaRepository<Notification,Long> {

    Notification findByUid(String uid);

    List<Notification> findByTarget(User target);

    Page<Notification> findByTargetOrderByCreatedDateTimeDesc(User target, Pageable pageable);

    @Transactional(readOnly = true)
    List<Notification> findFirst50ByNextAttemptTimeBeforeOrderByNextAttemptTimeAsc(Instant time);

    @Transactional(readOnly = true)
    List<Notification> findFirst100ByReadFalseAndCreatedDateTimeGreaterThan(Instant time);
}
