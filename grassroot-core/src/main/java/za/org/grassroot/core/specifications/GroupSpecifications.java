package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Group_;
import za.org.grassroot.core.domain.User;

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

}
