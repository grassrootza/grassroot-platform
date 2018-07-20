package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Group_;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.group.Membership_;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.EventSpecialForm;

import javax.persistence.criteria.Join;
import java.time.Instant;

/**
 * Created by luke on 2016/09/26.
 * Unfortunately it looks non-trivial to create a spec for the discriminator type ...
 */
public final class EventSpecifications {

    public static Specification<Event> upcomingEventsForUser(User user) {
        Specification<Event> basicProperties = (root, query, cb) -> cb.and(cb.isFalse(root.get("canceled")), cb.isTrue(root.get("rsvpRequired")),
                cb.greaterThan(root.get("eventStartDateTime"), Instant.now()));
        Specification<Event> userInAncestorGroup = (root, query, cb) -> {
            Join<Event, User> userGroupJoin = root.join("parentGroup").join("memberships");
            return cb.equal(userGroupJoin.get("user"), user);
        };

        // For the moment, response rates are relatively low, and hence having people see votes is more necessary
        // than, e.g., hypothetically preventing an organizer manipulating a vote by adding lots of people (and anyway,
        // that is easy to do by just adding them prior to vote). Hence removing this. _But_ it's a fiddly spec and
        // we may want it back in the future, so leaving it in here.

//        Specification<Event> userJoinedAfterVote = (root, query, cb) -> {
//            Join<Event, Membership> parentGroupMembership = root.join("ancestorGroup").join("memberships");
//            parentGroupMembership.on(cb.equal(parentGroupMembership.get("user").get("id"), user.getId()));
//            return cb.or(cb.notEqual(root.get("type"), EventType.VOTE),
//                    cb.greaterThan(root.get("createdDateTime"), parentGroupMembership.get("joinTime")));
//        };

        return Specification.where(basicProperties)
                .and(EventSpecifications.hasAllUsersAssignedOrIsAssigned(user))
                .and(userInAncestorGroup);
    }

    public static Specification<Event> hasAllUsersAssignedOrIsAssigned(User user) {
        return (root, query, cb) ->
                cb.or(cb.isEmpty(root.get("assignedMembers")), cb.isMember(user, root.get("assignedMembers")));
    }

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

    public static Specification<Vote> isOpenMassVoteForGroup(Group group) {
        return (root, query, cb) -> cb.and(
                cb.isFalse(root.get(Vote_.canceled)), cb.equal(root.get(Vote_.specialForm), EventSpecialForm.MASS_VOTE),
                cb.greaterThan(root.get(Vote_.eventStartDateTime), Instant.now()), cb.equal(root.get(Vote_.ancestorGroup), group));
    }

}
