package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.AbstractEventEntity_;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Event_;

import javax.persistence.criteria.Join;
import java.time.Instant;
import java.util.Collection;

/**
 * Created by luke on 2016/09/26.
 * Unfortunately it looks non-trivial to create a spec for the discriminator type ...
 */
public final class EventSpecifications {

    public static Specification<Event> hasGroupAsAncestor(Group group) {
        return (root, query, cb) -> cb.equal(root.get(Event_.ancestorGroup), group);
    }

    public static Specification<Event> hasGroupAsParent(Group group) {
        return (root, query, cb) -> cb.equal(root.get(AbstractEventEntity_.parentGroup), group);
    }

    public static Specification<Event> startDateTimeBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(AbstractEventEntity_.eventStartDateTime), start, end);
    }

    public static Specification<Event> startDateTimeAfter(Instant start) {
        return (root, query, cb) -> cb.greaterThan(root.get(AbstractEventEntity_.eventStartDateTime), start);
    }

    public static Specification<Event> createdDateTimeBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(AbstractEventEntity_.createdDateTime), start, end);
    }

    public static Specification<Event> notCancelled() {
        return (root, query, cb) -> cb.equal(root.get(Event_.canceled), false);
    }

    public static Specification<Event> isPublic() {
        return (root, query, cb) -> cb.equal(root.get(Event_.isPublic), true);
    }

    public static Specification<Event> userPartOfGroup(User user) {
        return (root, query, cb) -> {
            Join<Event, Group> groups = root.join(AbstractEventEntity_.parentGroup);
            Join<Group, Membership> members = groups.join(Group_.memberships);
            return cb.equal(members.get(Membership_.user), user);
        };
    }

}
