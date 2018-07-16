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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.notification.*;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

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
    private final TodoNotificationRepository todoNotificationRepository;

    @Autowired
    public NotificationManager(UserRepository userRepository, CacheManager cacheManager, NotificationRepository notificationRepository,
                               BroadcastNotificationRepository broadcastNotificationRepository,
                               EventNotificationRepository eventNotificationRepository, TodoNotificationRepository todoNotificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.broadcastNotificationRepository = broadcastNotificationRepository;
        this.cacheManager = cacheManager;
        this.eventNotificationRepository = eventNotificationRepository;
        this.todoNotificationRepository = todoNotificationRepository;
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
        return notificationRepository.findByTargetAndDeliveryChannelOrderByCreatedDateTimeDesc(target, DeliveryRoute.ANDROID_APP,
                PageRequest.of(pageNumber, pageSize));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> fetchSentOrBetterSince(String userUid, Instant sentSince, DeliveryRoute deliveryChannel) {
        Objects.requireNonNull(userUid);
        User target = userRepository.findOneByUid(userUid);
        Specification<Notification> specifications = Specification.where(toUser(target))
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
        notificationRepository.saveAll(notifications); // TX management not being super reliable on this
        Set<String> userUids = notifications.stream().map(n -> n.getTarget().getUid()).collect(Collectors.toSet());
        clearUnreadCaches(userUids);
    }

    @Override
    @Transactional
    public void markAllUserNotificationsRead(String userUid, Instant sinceTime) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Specification<Notification> specs = Specification
                .where(messageNotRead()).and(toUser(user)).and(createdTimeBetween(sinceTime, Instant.now()));
        List<Notification> unreadNotifications = notificationRepository.findAll(specs);
        unreadNotifications.forEach(n -> n.updateStatus(NotificationStatus.READ, false, false, null));
        notificationRepository.saveAll(unreadNotifications);
        // update cache, since this may happen & get a next call within 10 secs on front end
        clearUnreadCaches(Collections.singleton(userUid));
    }

    private void clearUnreadCaches(Collection<String> userUids) {
        Cache cache = cacheManager.getCache("user_notifications");
        cache.removeAll(userUids);
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
        Specification<Notification> specs = Specification.where(recently).and(isFailed).and(forGroup);
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
    public List<BroadcastNotification> loadAllNotificationsForBroadcast(Broadcast broadcast) {
        Specification<BroadcastNotification> forBroadcast = (root, query, cb) ->
                cb.equal(root.get(BroadcastNotification_.broadcast), broadcast);
        return broadcastNotificationRepository.findAll(forBroadcast);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BroadcastNotification> loadFailedNotificationsForBroadcast(String requestorUid, Broadcast broadcast) {
        Specification<BroadcastNotification> forBroadcast = (root, query, cb) ->
                cb.equal(root.get(BroadcastNotification_.broadcast), broadcast);
        Specification<BroadcastNotification> isFailed = (root, query, cb) ->
                root.get(BroadcastNotification_.status).in(FAILED_STATUS);
        Specification<BroadcastNotification> specs = Specification.where(forBroadcast).and(isFailed);
        return broadcastNotificationRepository.findAll(specs);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventNotification> loadFailedNotificationForEvent(String requestorUid, Event event) {
        Specification<EventNotification> forEvent = (root, query, cb) ->
                cb.equal(root.get(EventNotification_.event), event);
        Specification<EventNotification> isFailed = (root, query, cb) ->
                root.get(EventNotification_.status).in(FAILED_STATUS);
        Specification<EventNotification> specs = Specification.where(forEvent).and(isFailed);
        return eventNotificationRepository.findAll(specs);
    }

    @Override
    public List<TodoNotification> loadFailedNotificationForTodo(String requestorUid, Todo todo) {
        Specification<TodoNotification> forEvent = (root, query, cb) ->
                cb.equal(root.get(TodoNotification_.todo), todo);
        Specification<TodoNotification> isFailed = (root, query, cb) ->
                root.get(TodoNotification_.status).in(FAILED_STATUS);
        Specification<TodoNotification> specs = Specification.where(forEvent).and(isFailed);
        return todoNotificationRepository.findAll(specs);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFailedNotificationForEvent(String requestorUid, String eventUid) {
        Specification<EventNotification> forEvent = (root, query, cb) ->
                cb.equal(root.get(EventNotification_.event).get("uid"), eventUid);
        Specification<EventNotification> isFailed = (root, query, cb) ->
                root.get(BroadcastNotification_.status).in(FAILED_STATUS);
        Specification<EventNotification> specs = Specification.where(forEvent).and(isFailed);
        return eventNotificationRepository.count(specs);
    }

    @Override
    public long countFailedNotificationForTodo(String requestorUid, String todoUid) {
        Specification<TodoNotification> forEvent = (root, query, cb) ->
                cb.equal(root.get(TodoNotification_.todo).get("uid"), todoUid);
        Specification<TodoNotification> isFailed = (root, query, cb) ->
                root.get(TodoNotification_.status).in(FAILED_STATUS);
        Specification<TodoNotification> specs = Specification.where(forEvent).and(isFailed);
        return todoNotificationRepository.count(specs);
    }
}
