package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.association.AccountSponsorshipRequest;

/**
 * Created by luke on 2017/02/06.
 */
public interface AccountSponsorshipRequestRepository extends JpaRepository<AccountSponsorshipRequest, Long>,
        JpaSpecificationExecutor<AccountSponsorshipRequest> {

    AccountSponsorshipRequest findOneByUid(String uid);

}
