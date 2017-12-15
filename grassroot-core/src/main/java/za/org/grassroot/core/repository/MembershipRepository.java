package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    Membership findByGroupUidAndUserUid(String groupId, String userUid);

    List<Membership> findByGroupAndUserIn(Group group, List<User> users);

}
