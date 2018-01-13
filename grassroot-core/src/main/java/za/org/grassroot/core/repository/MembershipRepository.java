package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    Membership findByGroupUidAndUserUid(String groupId, String userUid);

    List<Membership> findByGroupAndUserIn(Group group, List<User> users);

    Page<Membership> findByGroupUid(String groupUid, Pageable pageable);

    // note: because of arrays, can't use specifications.
    @Query(value = "select m.* from group_user_membership m where " +
            "m.group_id = ?1 and m.tags && ?2 and m.join_time > ?3", nativeQuery = true)
    List<Membership> findByGroupTagsAndJoinedDateAfter(long groupId, String[] tags, Instant joinTime);

    @Query(value = "select m.* from group_user_membership m inner join user_profile u ON m.user_id = u.id " +
            "where m.group_id = ?1 and u.province in ?2 and m.tags && ?3 and m.join_time > ?4", nativeQuery = true)
    List<Membership> findByGroupProvinceTopicsAndJoinedDate(long groupId, List<Province> provinces, String[] tags, Instant joinTime);


}
