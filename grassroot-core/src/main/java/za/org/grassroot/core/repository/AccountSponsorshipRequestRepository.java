package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;
import za.org.grassroot.core.enums.AssocRequestStatus;

import java.util.List;

/**
 * Created by luke on 2017/02/06.
 */
public interface AccountSponsorshipRequestRepository extends JpaRepository<AccountSponsorshipRequest, Long> {

    AccountSponsorshipRequest findOneByUid(String uid);

    int countByRequestorAndDestinationAndStatus(Account requestor, User destination, AssocRequestStatus status);

    List<AccountSponsorshipRequest> findByDestinationAndStatus(User destination, AssocRequestStatus status, Sort sort);

    List<AccountSponsorshipRequest> findByRequestorAndStatus(Account requestor, AssocRequestStatus status, Sort sort);

}
