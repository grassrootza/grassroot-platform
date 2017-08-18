package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    long countByGroupUid(String groupUid);

}
