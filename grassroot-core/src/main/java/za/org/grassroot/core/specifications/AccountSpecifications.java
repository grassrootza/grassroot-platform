package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.domain.AccountBillingRecord_;
import za.org.grassroot.core.domain.Account_;
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

    public static Specification<AccountBillingRecord> forAccount(Account account) {
        return (root, query, cb) -> cb.equal(root.get(AccountBillingRecord_.account), account);
    }

    public static Specification<AccountBillingRecord> statementDateNotNull() {
        return (root, query, cb) -> cb.isNotNull(root.get(AccountBillingRecord_.statementDateTime));
    }

    public static Specification<AccountBillingRecord> paymentDateNotNull() {
        return (root, query, cb) -> cb.isNotNull(root.get(AccountBillingRecord_.nextPaymentDate));
    }

    public static Specification<AccountBillingRecord> paymentType(AccountPaymentType paymentType) {
        return (root, query, cb) -> cb.equal(root.get(AccountBillingRecord_.paymentType), paymentType);
    }

    public static Specification<AccountBillingRecord> statementDateBeforeOrderDesc(Instant endPeriod) {
        return (root, query, cb) -> {
            query.orderBy(cb.desc(root.get(AccountBillingRecord_.statementDateTime)));
            return cb.lessThan(root.get(AccountBillingRecord_.statementDateTime), endPeriod);
        };
    }

    public static Specification<AccountBillingRecord> statementDateAfterOrderAsc(Instant beginPeriod) {
        return (root, query, cb) -> {
            query.orderBy(cb.asc(root.get(AccountBillingRecord_.statementDateTime)));
            return cb.greaterThan(root.get(AccountBillingRecord_.statementDateTime), beginPeriod);
        };
    }

    public static Specification<AccountBillingRecord> createdBetween(Instant start, Instant end, boolean orderDescInSpec) {
        return (root, query, cb) -> {
            if (orderDescInSpec) {
                query.orderBy(cb.desc(root.get(AccountBillingRecord_.createdDateTime)));
            }
            return cb.between(root.get(AccountBillingRecord_.createdDateTime), start, end);
        };
    }

    public static Specification<AccountBillingRecord> isPaid(boolean paid) {
        return (root, query, cb) -> cb.equal(root.get(AccountBillingRecord_.paid), paid);
    }

    public static Specification<AccountBillingRecord> paymentDateBefore(Instant endPoint) {
        return (root, query, cb) -> cb.lessThan(root.get(AccountBillingRecord_.nextPaymentDate), endPoint);
    }

}
