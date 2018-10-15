package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    Membership findByGroupAndUser(Group group, User user);

    Membership findByGroupUidAndUserUid(String groupId, String userUid);

    List<Membership> findByGroupAndUserUidIn(Group group, Collection<String> userUids);

    List<Membership> findByGroupAndUserIn(Group group, Collection<User> users);

    Page<Membership> findByGroupUid(String groupUid, Pageable pageable);

    List<Membership> findByGroupUid(String groupUid);

    // note: because of arrays, can't use specifications.
    @Query(value = "select m.* from group_user_membership m where " +
            "m.group_id = ?1 and m.tags && ?2 and m.join_time > ?3", nativeQuery = true)
    List<Membership> findByGroupTagsAndJoinedDateAfter(long groupId, String[] tags, Instant joinTime);

    @Query(value = "select m.* from group_user_membership m inner join user_profile u ON m.user_id = u.id " +
            "where m.group_id = ?1 and u.province in ?2 and m.tags && ?3 and m.join_time > ?4", nativeQuery = true)
    List<Membership> findByGroupProvinceTopicsAndJoinedDate(long groupId, List<Province> provinces, String[] tags, Instant joinTime);

    // basically, every active membership, for populating graph ...
    List<Membership> findByGroupActiveTrue();

}
