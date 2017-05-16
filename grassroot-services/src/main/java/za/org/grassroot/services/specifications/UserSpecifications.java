package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.*;

import javax.persistence.criteria.Join;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;

/**
 * Created by luke on 2016/10/21.
 */
public final class UserSpecifications {

    public static Specification<User> createdAfter(Instant start) {
        return (root, query, cb) -> cb.greaterThan(root.get(User_.createdDateTime), start);
    }

    public static Specification<User> createdBefore(Instant end) {
        return (root, query, cb) -> cb.lessThan(root.get(User_.createdDateTime), end);
    }

    public static Specification<User> hasInitiatedSession() {
        return (root, query, cb) -> cb.equal(root.get(User_.hasInitiatedSession), true);
    }

    public static Specification<User> hasAndroidProfile() {
        return (root, query, cb) -> cb.equal(root.get(User_.hasAndroidProfile), true);
    }

    public static Specification<User> hasWebProfile() {
        return (root, query, cb) -> cb.equal(root.get(User_.hasWebProfile), true);
    }

    public static Specification<User> phoneContains(String phoneNumber) {
        return (root, query, cb) -> cb.like(root.get(User_.phoneNumber), "27" + phoneNumber);
    }

    public static Specification<User> inGroups(Set<Group> groups) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, Membership> userMembershipJoin = root.join(User_.memberships);
            return cb.isTrue(userMembershipJoin.get(Membership_.group).in(groups));
        };
    }

    public static Specification<User> nameContains(String nameFragment) {
        return (root, query, cb) -> cb.like(cb.lower(root.get(User_.displayName)), "%" + nameFragment.toLowerCase() + "%");
    }

    public static Specification<User> uidIn(Collection<String> uids) {
        return (root, query, cb) -> root.get(User_.uid).in(uids);
    }

    public static Specification<User> isLiveWireContact() {
        return (root, query, cb) -> cb.isTrue(root.get(User_.liveWireContact));
    }

}
