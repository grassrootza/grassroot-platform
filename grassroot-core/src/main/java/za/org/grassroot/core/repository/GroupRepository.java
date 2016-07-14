package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;


public interface GroupRepository extends JpaRepository<Group, Long> {
    /*
    Find the last group created by a specific user
     */
    Group findFirstByCreatedByUserOrderByIdDesc(User createdByUser);

    Group findOneByUid(String uid);

    Group findOneByImageUrl(String imageUrl);
    /*
    Get the sub-groups for a specific group
    one level only
     */
    List<Group> findByParentAndActiveTrue(Group parent);
    List<Group> findByParentOrderByIdAsc(Group parent);
    /*
    Find all the groups that a user is part of, with pagination
     */
    List<Group> findByMembershipsUser(User sessionUser);
    Page<Group> findByMembershipsUser(User sessionUser, Pageable pageable);

    /*
    Find groups which are active
     */
    List<Group> findByMembershipsUserAndActiveTrue(User user);
    Page<Group> findByMembershipsUserAndActive(User user, Pageable pageable, boolean active);

    int countByMembershipsUserAndActiveTrue(User user);

    /*
    Find a group by a code
     */
    Group findByGroupTokenCode(String groupTokenCode);
    Group findByGroupTokenCodeAndTokenExpiryDateTimeAfter(String groupTokenCode, Timestamp expiryTime);

    /*
    Find all groups, with pagination--for system admin
     */
    Page<Group> findAll(Pageable pageable);
    Page<Group> findAllByActiveOrderByIdAsc(boolean active, Pageable pageable);
    Long countByActive(boolean active);

    /*
    Couple of methods to be able to discover groups, public if user not a member, and their own groups
     */
    List<Group> findByGroupNameContainingIgnoreCaseAndDiscoverable(String nameFragment, boolean discoverable);
    List<Group> findByGroupNameContainingIgnoreCase(String nameFragment);
    List<Group> findByMembershipsUserAndGroupNameContainingIgnoreCaseAndActiveTrue(User user, String searchTerm);

    /*
    Methods for analytical service, to retrieve and count groups in periods (by created date time)
     */
    int countByCreatedDateTimeBetweenAndActive(Timestamp periodStart, Timestamp periodEnd, boolean active);

    /*
    Find the max(groupTokenCode) in table
    N.B. remove this when we stop using integer values
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(group_token_code as INTEGER)),123) FROM group_profile g WHERE group_token_code NOT LIKE ''", nativeQuery = true)
    int getMaxTokenValue();
    
    @Query(value = "select groupTokenCode from Group")
    List<String> findAllTokenCodes();

    /* find a group by id and return it and all it's subgroups
    .N.B. when adding columns to the table they must be added here is well
     */
    @Query(value = "WITH RECURSIVE tree(id, created_date_time, name, group_token_code, token_code_expiry, created_by_user, parent, version, reminderminutes) AS ( SELECT pg.* FROM group_profile pg WHERE pg.id = ?1 UNION ALL SELECT sg.* FROM group_profile sg, tree as nodes WHERE sg.parent = nodes.id ) SELECT * FROM tree",nativeQuery = true)
    List<Group> findGroupAndSubGroupsById(Long groupId);

    @Query(value = "with distinct_root as (select distinct q1.root, q1.id as member from (select g.id, getroot(g.id) as root from group_profile g, group_user_membership gu where gu.user_id = ?1 and gu.group_id = g.id  ) as q1) select distinct (getchildren(root)).*, root  from distinct_root order by root,parent", nativeQuery = true)
    List<Object[]> getGroupMemberTree(Long userId);

    @Query(value = "Select * from getusergroupswithsize(?1) where active = true", nativeQuery = true)
    List<Object[]> findActiveUserGroupsOrderedByRecentEvent(Long userId);

    @Query(value = "SELECT g FROM Group g WHERE g.id IN (SELECT gl.group.id FROM GroupLog gl WHERE (gl.createdDateTime BETWEEN ?1 AND ?2) AND gl.groupLogType = za.org.grassroot.core.enums.GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE)")
    List<Group> findGroupsWhereJoinCodeUsedBetween(Instant periodStart, Instant periodEnd);

    @Query(value = "SELECT g from Group g WHERE g.createdByUser = ?1 AND g.active = true AND LENGTH(g.groupName) < 2")
    List<Group> findActiveGroupsWithNamesLessThanOneCharacter(User createdByUser);

    @Query("select g from GroupLog gl inner join gl.group g inner join g.memberships m WHERE gl.groupLogType = 'GROUP_REMOVED' and gl.createdDateTime >= ?2 and m.user = ?1")
    List<Group> findMemberGroupsDeactivatedAfter(User member, Instant time);

    @Query("SELECT g from GroupLog gl inner join gl.group g WHERE gl.groupLogType = 'GROUP_MEMBER_REMOVED' and gl.userOrSubGroupId = ?1 AND gl.createdDateTime >= ?2")
    List<Group> findMembershipRemovedAfter(Long formerMemberId, Instant time);
}
