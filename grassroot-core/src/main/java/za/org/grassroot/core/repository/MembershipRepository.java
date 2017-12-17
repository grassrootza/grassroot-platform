package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Membership;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    long countByGroupUid(String groupUid);

    Membership findByGroupUidAndUserUid(String groupId, String userUid);

    Page<Membership> findByGroupUid(String groupUid, Pageable pageable);

}
