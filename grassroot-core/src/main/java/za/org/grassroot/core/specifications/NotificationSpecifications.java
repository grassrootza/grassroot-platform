package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.account.AccountLog_;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.DeliveryRoute;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by luke on 2016/10/06.
 */
public final class NotificationSpecifications {

    public static Specification<Notification> toUser(User target) {
        return (root, query, cb) -> cb.equal(root.get(Notification_.target), target);
    }

    public static Specification<Notification> wasDelivered() {
        List<NotificationStatus> deliveredStatuses = Arrays.asList(NotificationStatus.DELIVERED, NotificationStatus.READ);
        return (root, query, cb) -> root.get(Notification_.status).in(deliveredStatuses);
    }

    public static Specification<Notification> createdTimeBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(Notification_.createdDateTime), start, end);
    }


    public static Specification<Notification> belongsToAccount(final Account account) {
        return (root, query, cb) -> {
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog);
            return cb.equal(accountLogJoin.get(AccountLog_.account), account);
        };
    }

    public static Specification<Notification> accountLogTypeIs(final AccountLogType accountLogType) {
        return (root, query, cb) -> {
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog);
            return cb.equal(accountLogJoin.get(AccountLog_.accountLogType), accountLogType);
        };
    }

    public static Specifications<Notification> sentOrBetterSince(Instant time) {
        List<NotificationStatus> sentOrBetterStatuses = Arrays.asList(NotificationStatus.READ, NotificationStatus.DELIVERED, NotificationStatus.READ);
        Specification<Notification> sentOrBetter = (root, query, cb) -> root.get(Notification_.status).in(sentOrBetterStatuses);
        Specification<Notification> statusChangedSince = (root, query, cb) -> cb.greaterThan(root.get(Notification_.lastStatusChange), time);
        return Specifications.where(statusChangedSince).and(sentOrBetter);
    }

    public static Specification<Notification> ancestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog, JoinType.LEFT);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event, JoinType.LEFT);
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog, JoinType.LEFT);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo, JoinType.LEFT);
            Join<Notification, AccountLog> accountLogJoin = root.join(Notification_.accountLog, JoinType.LEFT);
            return cb.or(cb.or(cb.equal(eventJoin.get(Event_.ancestorGroup), group),
                    cb.equal(todoJoin.get(Todo_.ancestorGroup), group)), cb.equal(accountLogJoin.get(AccountLog_.group), group));
        };
    }

    public static Specification<Notification> forGroupBroadcast(Broadcast broadcast) {
        return (root, query, cb) -> {
          Join<Notification, GroupLog> groupLogJoin = root.join(Notification_.groupLog);
          return cb.equal(groupLogJoin.get(GroupLog_.broadcast), broadcast);
        };
    }

    public static Specification<Notification> getBySendingKey(String sendingKey) {
        return (root, query, cb) -> cb.equal(root.get(Notification_.sendingKey), sendingKey);
    }


    public static Specification<Notification> isInFailedStatus() {
        return (root, query, cb) -> root.get(Notification_.status).in(Arrays.asList(NotificationStatus.DELIVERY_FAILED,
                NotificationStatus.SENDING_FAILED, NotificationStatus.UNDELIVERABLE));
    }

    public static Specification<Notification> forDeliveryChannel(DeliveryRoute deliveryChannel) {
        return (root, query, cb) -> cb.equal(root.get(Notification_.deliveryChannel), deliveryChannel);
    }

    public static Specification<Notification> forDeliveryChannels(Collection<DeliveryRoute> deliveryChannels) {
        return (root, query, cb) -> root.get(Notification_.deliveryChannel).in(deliveryChannels);
    }

    public static Specifications<Notification> unReadUserNotifications(User target) {
        return Specifications.where(toUser(target))
                .and(createdTimeBetween(Instant.now().minus(3, ChronoUnit.DAYS), Instant.now()))
                .and(Specifications.not(wasDelivered()));
    }


}
