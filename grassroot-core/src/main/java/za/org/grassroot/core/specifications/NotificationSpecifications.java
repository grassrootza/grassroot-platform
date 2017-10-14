package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.account.AccountLog_;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.AccountLogType;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2016/10/06.
 */
public final class NotificationSpecifications {

    public static Specification<Notification> wasDelivered() {
        List<NotificationStatus> deliveredStatuzses = Arrays.asList(NotificationStatus.DELIVERED, NotificationStatus.READ);
        return (root, query, cb) -> root.get(Notification_.status).in(deliveredStatuzses);
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

    public static Specification<Notification> eventAncestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog, JoinType.LEFT);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event, JoinType.LEFT);
            return cb.equal(eventJoin.get(Event_.ancestorGroup), group);
        };
    }

    public static Specification<Notification> todoAncestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog, JoinType.LEFT);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo, JoinType.LEFT);
            return cb.equal(todoJoin.get(Todo_.ancestorGroup), group);
        };
    }

    public static Specification<Notification> ancestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog, JoinType.LEFT);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event, JoinType.LEFT);
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog, JoinType.LEFT);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo, JoinType.LEFT);
            return cb.or(cb.equal(eventJoin.get(Event_.ancestorGroup), group),
                    cb.equal(todoJoin.get(Todo_.ancestorGroup), group));
        };
    }

    public static Specification<Notification> groupLogIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, GroupLog> groupLogJoin = root.join(Notification_.groupLog);
            return cb.equal(groupLogJoin.get(GroupLog_.group), group);
        };
    }

    public static Specification<Notification> getBySendingKey(String sendingKey) {
        return (root, query, cb) -> cb.equal(root.get(Notification_.sendingKey), sendingKey);
    }


    public static Specification<Notification> isInFailedStatus() {
        return (root, query, cb) -> root.get(Notification_.status).in(Arrays.asList(NotificationStatus.DELIVERY_FAILED, NotificationStatus.SENDING_FAILED, NotificationStatus.UNDELIVERABLE));
    }




}