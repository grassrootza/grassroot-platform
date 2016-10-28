package za.org.grassroot.services.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Account_;

import java.time.Instant;

/**
 * Created by luke on 2016/10/25.
 */
public final class AccountSpecifications {

    public static Specification<Account> nextStatementBefore(Instant endPoint) {
        return (root, query, cb) -> cb.lessThan(root.get(Account_.nextBillingDate), endPoint);
    }

    public static Specification<Account> isEnabled() {
        return (root, query, cb) -> cb.isTrue(root.get(Account_.enabled));
    }

    public static Specification<Account> isVisible() {
        return (root, query, cb) -> cb.isTrue(root.get(Account_.visible));
    }

}
