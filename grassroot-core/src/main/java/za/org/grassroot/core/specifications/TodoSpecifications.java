package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.*;
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

    public static Specification<Todo> hasGroupAsAncestor(final Group group) {
        return (root, query, cb) -> cb.equal(root.get(Todo_.ancestorGroup), group);
    }

    public static Specification<Todo> notCancelled() {
        return (root, query, cb) -> cb.equal(root.get(Todo_.cancelled), false);
    }

    public static Specification<Todo> actionByDateAfter(Instant start) {
        return (root, query, cb) -> cb.greaterThan(root.get(AbstractTodoEntity_.actionByDate), start);
    }

    public static Specification<Todo> actionByDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(AbstractTodoEntity_.actionByDate), start, end);
    }

    public static Specification<Todo> createdDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(AbstractTodoEntity_.createdDateTime), start, end);
    }

    public static Specification<Todo> remindersLeftToSend() {
        return (root, query, cb) -> cb.and(cb.lessThan(root.get(Todo_.nextNotificationTime), Instant.now()),
                cb.equal(root.get(AbstractTodoEntity_.reminderActive), true));
    }

    public static Specification<Todo> reminderTimeBefore(Instant time) {
        return (root, query, cb) -> cb.lessThan(root.get(AbstractTodoEntity_.nextNotificationTime), time);
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
            Join<Todo, TodoAssignment> join = root.join(Todo_.assignments, JoinType.LEFT);

            return cb.or(cb.or(cb.isNull(join.get(TodoAssignment_.user)),
                    cb.or(cb.notEqual(root.get(AbstractTodoEntity_.createdByUser), join.get(TodoAssignment_.user))),
                    cb.notEqual(join.get(TodoAssignment_.confirmType), TodoCompletionConfirmType.COMPLETED)));
        };
    }

    public static Specifications<Todo> todosForUserResponse(User user) {
        Specification<Todo> isOfType = (root, query, cb) -> root.get(Todo_.type).in(TodoType.typesRequiringResponse());
        Specification<Todo> isAssigned = (root, query, cb) -> {
            Join<Todo, TodoAssignment> join = root.join(Todo_.assignments);
            return cb.and(
                    cb.equal(join.get(TodoAssignment_.user), user),
                    cb.isFalse(join.get(TodoAssignment_.hasResponded)),
                    cb.or(cb.isTrue(join.get(TodoAssignment_.assignedAction)),
                            cb.isTrue(join.get(TodoAssignment_.canWitness))));
        };
        return Specifications.where(isOfType).and(isAssigned);
    }

    public static Specification<TodoAssignment> userAssignment(User user, Todo todo) {
        return (root, query, cb) -> cb.and(cb.equal(root.get(TodoAssignment_.todo), todo),
                cb.equal(root.get(TodoAssignment_.user), user));
    }

}
