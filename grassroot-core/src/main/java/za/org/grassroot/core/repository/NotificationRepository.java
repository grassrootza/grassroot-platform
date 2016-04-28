package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationRepository extends JpaRepository<Notification,Long> {

    Notification findByUid(String uid);

    List<Notification> findByCreatedDateTimeLessThanAndDelivered(Instant instant, boolean delivered);

    List<Notification> findByTarget(User user);

    Page<Notification> findByTarget(User user, Pageable pageable);

}
