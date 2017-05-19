package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.AccountLog;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.TodoLog;
import za.org.grassroot.core.enums.AccountLogType;

import javax.persistence.criteria.Join;
import java.time.Instant;

/**
 * Created by luke on 2016/10/06.
 */
public final class NotificationSpecifications {

    public static Specification<Notification> wasDelivered() {
        return (root, query, cb) -> cb.equal(root.get(Notification_.delivered), true);
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
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event);
            return cb.equal(eventJoin.get(Event_.ancestorGroup), group);
        };
    }

    public static Specification<Notification> todoAncestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo);
            return cb.equal(todoJoin.get(Todo_.ancestorGroup), group);
        };
    }

    public static Specification<Notification> ancestorGroupIs(final Group group) {
        return (root, query, cb) -> {
            Join<Notification, EventLog> eventLogJoin = root.join(Notification_.eventLog);
            Join<EventLog, Event> eventJoin = eventLogJoin.join(EventLog_.event);
            Join<Notification, TodoLog> todoLogJoin = root.join(Notification_.todoLog);
            Join<TodoLog, Todo> todoJoin = todoLogJoin.join(TodoLog_.todo);
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

}
