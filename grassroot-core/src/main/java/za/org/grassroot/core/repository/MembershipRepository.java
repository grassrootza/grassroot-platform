package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;

import java.util.Collection;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    Membership findByGroupAndUser(Group group, User user);

    Membership findByGroupUidAndUserUid(String groupId, String userUid);

    List<Membership> findByGroupAndUserUidIn(Group group, Collection<String> userUids);

    List<Membership> findByGroupAndUserIn(Group group, Collection<User> users);

    Page<Membership> findByGroupUid(String groupUid, Pageable pageable);

    List<Membership> findByGroupUid(String groupUid);
}
