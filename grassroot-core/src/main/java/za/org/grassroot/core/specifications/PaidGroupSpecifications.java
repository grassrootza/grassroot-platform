package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.account.PaidGroup;
import za.org.grassroot.core.domain.account.PaidGroup_;
import za.org.grassroot.core.enums.PaidGroupStatus;

import java.time.Instant;

/**
 * Created by luke on 2016/10/21.
 */
public final class PaidGroupSpecifications {

    public static Specification<PaidGroup> isForAccount(final Account account) {
        return (root, query, cb) -> cb.equal(root.get(PaidGroup_.account), account);
    }

    public static Specification<PaidGroup> isForGroup(final Group group) {
        return (root, query, cb) -> cb.equal(root.get(PaidGroup_.group), group);
    }

    public static Specification<PaidGroup> expiresAfter(final Instant time) {
        return (root, query, cb) -> cb.greaterThan(root.get(PaidGroup_.expireDateTime), time);
    }

    public static Specification<PaidGroup> hasStatus(final PaidGroupStatus status) {
        return (root, query, cb) -> cb.equal(root.get(PaidGroup_.status), status);
    }

}
