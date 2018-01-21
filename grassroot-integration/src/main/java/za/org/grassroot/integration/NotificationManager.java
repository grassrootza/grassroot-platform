package za.org.grassroot.integration;

import lombok.extern.slf4j.Slf4j;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.NotificationStatus;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.NotificationSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */
@Slf4j
@Service
public class NotificationManager implements NotificationService{

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final CacheManager cacheManager;

    @Autowired
    public NotificationManager(UserRepository userRepository,
                               NotificationRepository notificationRepository,
                               CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Notification loadNotification(String uid) {
        Objects.requireNonNull(uid);
        return notificationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> fetchPagedAndroidNotifications(User target, int pageNumber, int pageSize) {
        return notificationRepository.findByTargetAndDeliveryChannelOrderByCreatedDateTimeDesc(target, DeliveryRoute.ANDROID_APP, new PageRequest(pageNumber, pageSize));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> fetchSentOrBetterSince(String userUid, Instant sentSince, DeliveryRoute deliveryChannel) {
        Objects.requireNonNull(userUid);
        User target = userRepository.findOneByUid(userUid);
        Specifications<Notification> specifications = Specifications.where(NotificationSpecifications.toUser(target))
                .and(NotificationSpecifications.sentOrBetterSince(sentSince));
        if (deliveryChannel != null) {
            specifications = specifications.and(NotificationSpecifications.forDeliveryChannel(deliveryChannel));
        }
        return notificationRepository.findAll(specifications);
    }

    @Override
    @Transactional
    public void updateNotificationsViewedAndRead(Set<String> notificationUids) {
        List<Notification> notifications = notificationRepository.findByUidIn(notificationUids);
        notifications.forEach(n -> n.updateStatus(NotificationStatus.READ, false, false, null));
    }

    @Override
    @Transactional(readOnly = true)
    public int countUnviewedAndroidNotifications(String targetUid) {
        User user = userRepository.findOneByUid(targetUid);
        return notificationRepository.countByTargetAndDeliveryChannelAndStatusNot(user, DeliveryRoute.ANDROID_APP, NotificationStatus.READ);
    }

    @Override
    public List<Notification> loadRecentFailedNotificationsInGroup(LocalDateTime from, LocalDateTime to, Group group) {
        Instant fromInstant = DateTimeUtil.convertToSystemTime(from, ZoneId.systemDefault());
        Instant toInstant = DateTimeUtil.convertToSystemTime(to, ZoneId.systemDefault());
        Specification<Notification> recently = NotificationSpecifications.createdTimeBetween(fromInstant, toInstant);
        Specification<Notification> isFailed = NotificationSpecifications.isInFailedStatus();
        Specification<Notification> forGroup = NotificationSpecifications.ancestorGroupIs(group);
        Specifications<Notification> specs = Specifications.where(recently).and(isFailed).and(forGroup);
        return notificationRepository.findAll(specs);
    }

    @Override
    public List<Notification> fetchUnreadUserNotifications(User target, Sort sort) {

        // cache is here just to ensure that DB will not be queried more then once in 10 second even if someone try that from client side
        Cache cache = cacheManager.getCache("user_notifications");
        String cacheKey = target.getUid();
        Element element = cache.get(cacheKey);
        List<Notification> resultFromCache = element != null ? (List<Notification>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        return notificationRepository.findAll(NotificationSpecifications.unReadUserNotifications(target), sort);
    }
}
