package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    Membership findByGroupAndUser(Group group, User user);

    Membership findByGroupUidAndUserUid(String groupId, String userUid);

    List<Membership> findByGroupAndUserUidIn(Group group, Collection<String> userUids);

    List<Membership> findByGroupAndUserIn(Group group, Collection<User> users);

    Page<Membership> findByGroupUid(String groupUid, Pageable pageable);

    List<Membership> findByGroupUid(String groupUid);

    int countByGroup(Group group);

    @Query("select distinct tags from za.org.grassroot.core.domain.group.Membership where tags is not null and group = ?1")
    Set<String[]> findDistinctMembershipTagsByGroup(Group group);
}
