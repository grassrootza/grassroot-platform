package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;

import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.Set;

/**
 * Created by luke on 2017/02/14.
 */
public final class GroupSpecifications {

    public static Specification<Group> createdByUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(Group_.createdByUser), user);
    }

    public static Specification<Group> userIsMember(User user) {
        return (root, query, cb) -> {
            Join<Group, Membership> members = root.join(Group_.memberships);
            return cb.equal(members.get(Membership_.user), user);
        };
    }

    public static Specification<Group> userIsMemberAndCanSeeMembers(User user) {
        return (root, query, cb) -> {
            Join<Group, Membership> members = root.join(Group_.memberships);
            Predicate isMember = cb.equal(members.get(Membership_.user), user);
            Predicate canSeeMembers = cb.isMember(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS, members.get(Membership_.role).get(Role_.permissions));
            return cb.and(isMember, canSeeMembers);
        };
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

    public static Specification<Group> hasJoinCode(String joinCode) {
        return (root, query, cb) -> cb.equal(root.get(Group_.groupTokenCode), joinCode);
    }

    public static Specification<Group> joinCodeExpiresAfter(Instant time) {
        return (root, query, cb) -> cb.greaterThan(root.get(Group_.tokenExpiryDateTime), time);
    }

    public static Specification<Group> createdBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(Group_.createdDateTime), start, end);
    }

    public static Specification<Group> hasParent(Group parent) {
        return (root, query, cb) -> cb.equal(root.get(Group_.parent), parent);
    }

    public static Specification<Group> hasImageUrl(String imageUrl) {
        return (root, query, cb) -> cb.equal(root.get(Group_.imageUrl), imageUrl);
    }


}
