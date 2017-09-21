package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest_;
import za.org.grassroot.core.enums.AssocRequestStatus;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2017/02/10.
 */
public final class SponsorRequestSpecifications {

    public static Specification<AccountSponsorshipRequest> hasStatus(AssocRequestStatus status) {
        return (root, query, cb) -> cb.equal(root.get(AccountSponsorshipRequest_.status), status);
    }

    public static Specification<AccountSponsorshipRequest> hasStatus(List<AssocRequestStatus> statusList) {
        return (root, query, cb) -> root.get(AccountSponsorshipRequest_.status).in(statusList);
    }

    public static Specification<AccountSponsorshipRequest> forAccount(Account account) {
        return (root, query, cb) -> cb.equal(root.get(AccountSponsorshipRequest_.requestor), account);
    }

    public static Specification<AccountSponsorshipRequest> toUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(AccountSponsorshipRequest_.destination), user);
    }

    public static Specification<AccountSponsorshipRequest> createdBefore(Instant instant) {
        return (root, query, cb) -> cb.lessThan(root.get(AccountSponsorshipRequest_.creationTime), instant);
    }

}
