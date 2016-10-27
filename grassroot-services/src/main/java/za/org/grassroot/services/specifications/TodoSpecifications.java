package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import java.time.Instant;

/**
 * Created by luke on 2016/09/15.
 */
public final class TodoSpecifications {

    public static Specification<Todo> createdByUser(final User user) {
        return (root, query, cb) -> cb.equal(root.get(AbstractTodoEntity_.createdByUser), user);
    }

    public static Specification<Todo> messageIs(final String message) {
        return (root, query, cb) -> cb.equal(root.get(AbstractTodoEntity_.message), message);
    }

    public static Specification<Todo> hasGroupAsParent(final Group group) {
        return (root, query, cb) -> cb.equal(root.get(AbstractTodoEntity_.parentGroup), group);
    }

    public static Specification<Todo> notCancelled() {
        return (root, query, cb) -> cb.equal(root.get(Todo_.cancelled), false);
    }

    public static Specification<Todo> actionByDateAfter(Instant start) {
        return (root, query, cb) -> cb.greaterThan(root.get(Todo_.actionByDate), start);
    }

    public static Specification<Todo> actionByDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(Todo_.actionByDate), start, end);
    }

    public static Specification<Todo> completionConfirmsAbove(double threshold) {
        return (root, query, cb) -> cb.greaterThan(root.get(Todo_.completionPercentage), threshold);
    }

    public static Specification<Todo> completionConfirmsBelow(double threshold) {
        return (root, query, cb) -> cb.lessThan(root.get(Todo_.completionPercentage), threshold);
    }

    public static Specification<Todo> createdDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(AbstractTodoEntity_.createdDateTime), start, end);
    }

    public static Specification<Todo> remindersLeftToSend() {
        return (root, query, cb) -> cb.and(cb.greaterThan(root.get(Todo_.numberOfRemindersLeftToSend), 0),
                cb.equal(root.get(Todo_.reminderActive), true));
    }

    public static Specification<Todo> reminderTimeBefore(Instant time) {
        return (root, query, cb) -> cb.lessThan(root.get(AbstractTodoEntity_.scheduledReminderTime), time);
    }

    public static Specification<Todo> userPartOfGroup(final User user) {
        return (root, query, cb) -> {
            Join<Todo, Group> groups = root.join(AbstractTodoEntity_.parentGroup);
            Join<Group, Membership> members = groups.join(Group_.memberships);
            return cb.equal(members.get(Membership_.user), user);
        };
    }

    public static Specification<Todo> todoNotConfirmedByCreator() {
        return (root, query, cb) -> {
            // note : keep an eye on this (e.g., whether not should go in here, or outside
            query.distinct(true);
            Join<Todo, TodoCompletionConfirmation> join = root.join(Todo_.completionConfirmations, JoinType.LEFT);

            /*return cb.not(cb.and(
                    cb.equal(root.get(Todo_.createdByUser), join.get(TodoCompletionConfirmation_.member)),
                    cb.equal(join.get(TodoCompletionConfirmation_.confirmType), TodoCompletionConfirmType.COMPLETED)));*/
            return cb.or(cb.or(cb.isNull(join.get(TodoCompletionConfirmation_.member)),
                    cb.or(cb.notEqual(root.get(Todo_.createdByUser), join.get(TodoCompletionConfirmation_.member))),
                    cb.notEqual(join.get(TodoCompletionConfirmation_.confirmType), TodoCompletionConfirmType.COMPLETED)));
        };
    }

}
