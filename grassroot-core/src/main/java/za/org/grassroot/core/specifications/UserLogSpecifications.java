package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.UserLog_;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;

import java.time.Instant;

/**
 * Created by luke on 2017/04/24.
 */
public final class UserLogSpecifications {

    public static Specification<UserLog> forUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(UserLog_.userUid), user.getUid());
    }

    public static Specification<UserLog> forUser(String userUid) {
        return (root, query, cb) -> cb.equal(root.get(UserLog_.userUid), userUid);
    }

    public static Specification<UserLog> ofType(UserLogType type) {
        return (root, query, cb) -> cb.equal(root.get(UserLog_.userLogType), type);
    }

    public static Specification<UserLog> creationTimeBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.between(root.get(UserLog_.creationTime), start, end);
    }

    public static Specification<UserLog> usingInterface(UserInterfaceType type) {
        return (root, query, cb) -> cb.equal(root.get(UserLog_.userInterface), type);
    }

    public static Specification<UserLog> hasDescription(String description) {
        return (root, query, cb) -> cb.equal(root.get(UserLog_.description), description);
    }

}
