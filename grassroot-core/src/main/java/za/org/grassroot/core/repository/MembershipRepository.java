package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Membership;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor {

    long countByGroupUid(String groupUid);

}
