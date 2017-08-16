package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.Account_;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;

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

    public static Specification<Account> defaultPaymentType(AccountPaymentType paymentType) {
        return (root, query, cb) -> cb.equal(root.get(Account_.defaultPaymentType), paymentType);
    }

    public static Specification<Account> billingCycle(AccountBillingCycle billingCycle) {
        return (root, query, cb) -> cb.equal(root.get(Account_.billingCycle), billingCycle);
    }
}
