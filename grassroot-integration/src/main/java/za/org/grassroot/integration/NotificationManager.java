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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.BroadcastNotification;
import za.org.grassroot.core.domain.notification.BroadcastNotification_;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.notification.EventNotification_;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.repository.BroadcastNotificationRepository;
import za.org.grassroot.core.repository.EventNotificationRepository;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static za.org.grassroot.core.specifications.NotificationSpecifications.*;

/**
 * Created by paballo on 2016/04/07.
 */
@Slf4j
@Service
public class NotificationManager implements NotificationService{

    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    private final NotificationRepository notificationRepository;
    private final BroadcastNotificationRepository broadcastNotificationRepository;
    private final EventNotificationRepository eventNotificationRepository;

    @Autowired
    public NotificationManager(UserRepository userRepository,
                               NotificationRepository notificationRepository,
                               BroadcastNotificationRepository broadcastNotificationRepository,
                               CacheManager cacheManager, EventNotificationRepository eventNotificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.broadcastNotificationRepository = broadcastNotificationRepository;
        this.cacheManager = cacheManager;
        this.eventNotificationRepository = eventNotificationRepository;
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
        Specifications<Notification> specifications = Specifications.where(toUser(target))
                .and(sentOrBetterSince(sentSince));
        if (deliveryChannel != null) {
            specifications = specifications.and(forDeliveryChannel(deliveryChannel));
        }
        return notificationRepository.findAll(specifications);
    }

    @Override
    @Transactional
    public void updateNotificationsViewedAndRead(Set<String> notificationUids) {
        List<Notification> notifications = notificationRepository.findByUidIn(notificationUids);
        notifications.forEach(n -> n.updateStatus(NotificationStatus.READ, false, false, null));
        notificationRepository.save(notifications); // TX management not being super reliable on this
    }

    @Override
    @Transactional
    public void markAllUserNotificationsRead(String userUid, Instant sinceTime) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Specifications<Notification> specs = Specifications
                .where(messageNotRead()).and(toUser(user)).and(createdTimeBetween(sinceTime, Instant.now()));
        List<Notification> unreadNotifications = notificationRepository.findAll(specs);
        unreadNotifications.forEach(n -> n.updateStatus(NotificationStatus.READ, false, false, null));
        notificationRepository.save(unreadNotifications);
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
        Specification<Notification> recently = createdTimeBetween(fromInstant, toInstant);
        Specification<Notification> isFailed = isInFailedStatus();
        Specification<Notification> forGroup = ancestorGroupIsTimeLimited(group, fromInstant);
        Specifications<Notification> specs = Specifications.where(recently).and(isFailed).and(forGroup);
        return notificationRepository.findAll(specs);
    }

    @Override
    public List<Notification> fetchUnreadUserNotifications(User target, Instant since, Sort sort) {

        // cache is here just to ensure that DB will not be queried more then once in 10 second even if someone try that from client side
        Cache cache = cacheManager.getCache("user_notifications");
        String cacheKey = target.getUid();
        Element element = cache.get(cacheKey);
        List<Notification> resultFromCache = element != null ? (List<Notification>) element.getObjectValue() : null;

        if (resultFromCache != null)
            return resultFromCache;

        return notificationRepository.findAll(unReadUserNotifications(target, since), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BroadcastNotification> loadFailedNotificationsForBroadcast(String requestorUid, Broadcast broadcast) {
        Specification<BroadcastNotification> forBroadcast = (root, query, cb) ->
                cb.equal(root.get(BroadcastNotification_.broadcast), broadcast);
        Specification<BroadcastNotification> isFailed = (root, query, cb) ->
                root.get(BroadcastNotification_.status).in(FAILED_STATUS);
        Specifications<BroadcastNotification> specs = Specifications.where(forBroadcast).and(isFailed);
        return broadcastNotificationRepository.findAll(specs);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventNotification> loadFailedNotificationForEvent(String requestorUid, Event event) {
        Specification<EventNotification> forEvent = (root, query, cb) ->
                cb.equal(root.get(EventNotification_.event), event);
        Specification<EventNotification> isFailed = (root, query, cb) ->
                root.get(BroadcastNotification_.status).in(FAILED_STATUS);
        Specifications<EventNotification> specs = Specifications.where(forEvent).and(isFailed);
        return eventNotificationRepository.findAll(specs);
    }
}
