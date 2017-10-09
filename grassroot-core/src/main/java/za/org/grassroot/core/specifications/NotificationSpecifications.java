package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.account.AccountLog_;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.domain.task.EventLog_;
import za.org.grassroot.core.domain.task.Event_;
import za.org.grassroot.core.domain.task.TodoLog_;
import za.org.grassroot.core.domain.task.Todo_;
import za.org.grassroot.core.enums.AccountLogType;

import javax.persistence.criteria.Join;
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
