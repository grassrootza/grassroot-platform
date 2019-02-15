package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface GroupRepository extends JpaRepository<Group, Long>, JpaSpecificationExecutor<Group> {

    Group findOneByUid(String uid);

    Set<Group> findByUidIn(Set<String> uids);

    /*
    Find the last group created by a specific user
     */
    Group findFirstByCreatedByUserAndActiveTrueOrderByIdDesc(User createdByUser);
    Group findFirstByCreatedByUserAndGroupNameAndCreatedDateTimeAfterAndActiveTrue(User createdByUser, String groupName, Instant createdSince);

    // Find all the groups that a user is part of, with pagination
    List<Group> findByMembershipsUserAndActiveTrueAndParentIsNull(User user);
    List<Group> findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(User user);
    Page<Group> findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(User user, Pageable pageable);

    int countByMembershipsUserAndActiveTrue(User user);

    /*
    Find all groups, with pagination--for system admin
     */
    Page<Group> findAll(Pageable pageable);

    /*
    Couple of methods to be able to discover groups, public if user not a member, and their own groups
     */
    @Query(value =
            "select g.* from group_profile g where " +
            "g.discoverable = true and " +
            "g.id not in (select m.group_id from group_user_membership m where m.user_id = ?1) and " +
            "(" +
            "to_tsvector('english', g.name) @@ to_tsquery('english', ?2) or " +
            "g.id in (select e.ancestor_group_id from event e where to_tsvector('english', e.name) @@ to_tsquery('english', ?2)) or " +
            "g.id in (select l.ancestor_group_id from action_todo l where to_tsvector('english', l.message) @@ to_tsquery('english', ?2))" +
            ")", nativeQuery = true)
    List<Group> findDiscoverableGroupsWithNameOrTaskTextWithoutMember(Long userId, String tsQuery);

    @Query(value = "select g.* from group_profile g where " +
            "g.discoverable = true and " +
            "g.id not in (select m.group_id from group_user_membership m where m.user_id = ?1) and " +
            "(to_tsvector('english', g.name) @@ to_tsquery('english', ?2))", nativeQuery = true)
    List<Group> findDiscoverableGroupsWithNameWithoutMember(Long userId, String tsQuery);

    List<Group> findByGroupNameContainingIgnoreCase(String nameQuery);

    @Query(value = "select g.* from group_profile g " +
            "inner join group_user_membership m on g.id = m.group_id " +
            "where g.active = true and m.user_id = ?1 and to_tsvector('english', g.name) @@ to_tsquery('english', ?2)",
            nativeQuery = true)
    List<Group> findByActiveAndMembershipsUserWithNameContainsText(Long userId, String nameTsQuery);

    @Query(value = "select g.* from group_profile g " +
            "inner join group_user_membership m on g.id = m.group_id " +
            "inner join group_role_permission rp on m.role = rp.role and g.id = rp.group_id " +
            "where g.active = true and g.parent is null and m.user_id = ?1 and rp.permission = ?2 " +
            "and to_tsvector('english', g.name) @@ to_tsquery('english', ?3) " +
            "order by greatest(g.last_task_creation_time, g.last_log_creation_time) desc", nativeQuery = true)
    Page<Group> findUsersGroupsWithSearchTermOrderedByActivity(Long userId, String permission, String nameTsQuery, Pageable pageable);

    @Query(value = "select g.* from group_profile g " +
            "inner join group_user_membership m on g.id = m.group_id " +
            "inner join group_role_permission rp on m.role = rp.role and g.id = rp.group_id " +
            "where g.active = true and g.parent is null and m.user_id = ?1 and rp.permission = ?2 " +
            "order by greatest(g.last_task_creation_time, g.last_log_creation_time) desc", nativeQuery = true)
    Page<Group> findUsersGroupsOrderedByActivity(Long userId, String permission, Pageable pageable);

    @Query(value = "select groupTokenCode from Group")
    List<String> findAllTokenCodes();

    @Query(value = "SELECT g FROM Group g WHERE g.active = true AND g.id IN (SELECT gl.group.id FROM GroupLog gl WHERE (gl.createdDateTime BETWEEN ?1 AND ?2) AND gl.groupLogType = za.org.grassroot.core.enums.GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE)")
    List<Group> findGroupsWhereJoinCodeUsedBetween(Instant periodStart, Instant periodEnd);

    @Query("select g from GroupLog gl inner join gl.group g inner join g.memberships m WHERE gl.groupLogType = 'GROUP_REMOVED' and gl.createdDateTime >= ?2 and m.user = ?1")
    List<Group> findMemberGroupsDeactivatedAfter(User member, Instant time);

    @Query("SELECT g from GroupLog gl inner join gl.group g WHERE gl.groupLogType = 'GROUP_MEMBER_REMOVED' and gl.targetUser = ?1 AND gl.createdDateTime >= ?2")
    List<Group> findMembershipRemovedAfter(User formerMember, Instant time);

    @Query(value = "select count(*) from Membership m inner join m.group g inner join g.rolePermissions rp where m.user = ?1 and m.role = rp.role and rp.permission = ?2 and g.active = true")
    int countActiveGroupsWhereMemberHasPermission(User member, Permission requiredPermission);

    @Query("select distinct g.id from Membership m inner join m.group g inner join g.rolePermissions rp where m.user = ?1 and m.role = rp.role and rp.permission = ?2")
    List<Long> findGroupIdsWhereMemberHasPermission(User member, Permission permission);

    @Query("SELECT g from Group g where " +
            "size(g.memberships) > :minSize")
    List<Group> findBySizeAbove(@Param(value="minSize") int minSize);

    @Query("SELECT g from Group g where " +
            "(size(g.descendantEvents) + size(g.descendantTodos)) > :minSize")
    List<Group> findByTasksMoreThan(@Param(value = "minSize") int minSize);

    @Query("SELECT count(*) from Group g " +
            "where size(g.memberships) < :limit")
    int countGroupsWhereSizeBelowLimit(@Param(value = "limit") int limit);

    @Query("SELECT count(*) from Group g " +
            "where size(g.memberships) > :limit")
    int countGroupsWhereSizeAboveLimit(@Param(value = "limit") int limit);

}
