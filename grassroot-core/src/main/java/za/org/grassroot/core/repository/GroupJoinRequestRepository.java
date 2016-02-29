package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.GroupJoinRequest;

public interface GroupJoinRequestRepository extends JpaRepository<GroupJoinRequest, Long> {
    GroupJoinRequest findOneByUid(String uid);
}
