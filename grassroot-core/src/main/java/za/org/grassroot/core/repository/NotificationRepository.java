package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.NotificationStatus;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.DeliveryRoute;

import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */
public interface NotificationRepository extends JpaRepository<Notification,Long>, JpaSpecificationExecutor<Notification> {

    Notification findByUid(String uid);

    List<Notification> findByUidIn(Set<String> uids);

    Page<Notification> findByTargetAndDeliveryChannelOrderByCreatedDateTimeDesc(User target, DeliveryRoute deliveryChannel, Pageable pageable);

    int countByTargetAndDeliveryChannelAndStatusNot(User target, DeliveryRoute deliveryChannel, NotificationStatus status);

}
