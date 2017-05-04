package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Group_;
import za.org.grassroot.core.domain.User;

import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import java.time.Instant;
import java.util.Set;

/**
 * Created by luke on 2017/02/14.
 */
public final class GroupSpecifications {

    public static Specification<Group> createdByUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(Group_.createdByUser), user);
    }

    public static Specification<Group> paidForStatus(boolean isPaidFor) {
        return (root, query, cb) -> cb.equal(root.get(Group_.paidFor), isPaidFor);
    }

    public static Specification<Group> isActive() {
        return (root, query, cb) -> cb.equal(root.get(Group_.active), true);
    }

    public static Specification<Group> uidIn(Set<String> uids) {
        return (root, query, cb) -> root.get(Group_.uid).in(uids);
    }

    public static Specification<Group> isPublic() {
        return (root, query, cb) -> cb.equal(root.get(Group_.discoverable), true);
    }

/*    public static Specification<Group> sizeAbove(int minimumSize) {
        return (root, query, cb) -> {
            // CriteriaQuery<Long> cq =
            CriteriaQuery<Long>
            return cb.greaterThan(root.get(Group_.memberships), minimumSize);
        };
    }

    public static Specification<Group> createdAfter(Instant start) {
        return (root, query, cb) -> cb.greaterThan(root.get(Group_.createdDateTime), start);
    }

    public static Specification<Group> tasksAbove(int minimumTasks) {
        return (root, query, cb) -> {
            Join<Group, Event> eventJoin = root.join(Group_.events);
            return cb.count(root.get(Group_.events));
            // int numberEvents = eventJoin
        };
    }*/

}
