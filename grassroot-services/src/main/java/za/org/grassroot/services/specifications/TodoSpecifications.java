package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;

import javax.persistence.criteria.Join;
import java.time.Instant;

/**
 * Created by luke on 2016/09/15.
 */
public final class TodoSpecifications {

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

    public static Specification<Todo> userPartOfGroup(final User user) {
        return (root, query, cb) -> {
            Join<Todo, Group> groups = root.join(AbstractTodoEntity_.parentGroup);
            Join<Group, Membership> members = groups.join(Group_.memberships);
            return cb.equal(members.get("user"), user); // switch this to type safe when static meta model generation is fixed
        };
    }

    public static Specification<Todo> userAssigned(final User user) {
        return (root, query, cb) -> cb.isMember(user, root.get(Todo_.assignedMembers));
    }

}
