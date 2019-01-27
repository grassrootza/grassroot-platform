package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.Role_;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.User_;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.group.Membership_;

import javax.persistence.criteria.Join;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

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

    public static Specification<User> isEnabled() {
        return (root, query, cb) -> cb.isTrue(root.get(User_.enabled));
    }

    public static Specification<User> hasAndroidProfile() {
        return (root, query, cb) -> cb.equal(root.get(User_.hasAndroidProfile), true);
    }

    public static Specification<User> hasWebProfile() {
        return (root, query, cb) -> cb.equal(root.get(User_.hasWebProfile), true);
    }

    public static Specification<User> hasWhatsAppOptIn() {
        return (root, query, cb) -> cb.equal(root.get(User_.whatsAppOptedIn), true);
    }

    public static Specification<User> phoneContains(String phoneNumber) {
        return (root, query, cb) -> cb.like(root.get(User_.phoneNumber), "27" + phoneNumber);
    }

    public static Specification<User> inGroups(Collection<Group> groups) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<User, Membership> userMembershipJoin = root.join(User_.memberships);
            return cb.isTrue(userMembershipJoin.get(Membership_.group).in(groups));
        };
    }

    public static Specification<User> nameContains(String nameFragment) {
        return (root, query, cb) -> cb.like(cb.lower(root.get(User_.displayName)), "%" + nameFragment.toLowerCase() + "%");
    }

    public static Specification<User> withNameInGroups(String nameFragment, List<Group> groups) {
        return Specification.where(inGroups(groups)).and(nameContains(nameFragment));
    }

    public static Specification<User> uidIn(Collection<String> uids) {
        return (root, query, cb) -> root.get(User_.uid).in(uids);
    }

    public static Specification<User> isLiveWireContact() {
        return (root, query, cb) -> cb.isTrue(root.get(User_.liveWireContact));
    }
}
