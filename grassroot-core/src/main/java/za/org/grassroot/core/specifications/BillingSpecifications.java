package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;
import za.org.grassroot.core.domain.account.AccountBillingRecord_;
import za.org.grassroot.core.enums.AccountPaymentType;

import java.time.Instant;

/**
 * Created by luke on 2017/02/13.
 */
public final class BillingSpecifications {

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
