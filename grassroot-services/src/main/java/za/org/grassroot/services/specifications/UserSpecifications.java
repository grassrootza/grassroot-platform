package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.User_;

import java.time.Instant;

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
    
}
