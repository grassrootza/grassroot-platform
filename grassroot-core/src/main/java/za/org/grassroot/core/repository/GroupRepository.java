package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.GroupDTO;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;


public interface GroupRepository extends JpaRepository<Group, Long> {
    /*
    Find all the groups created by a specific user
     */
    List<Group> findByCreatedByUser(User createdByUser);
    List<Group> findByCreatedByUserAndActiveOrderByCreatedDateTimeDesc(User createdByUser, boolean active);
    /*
    Find the last group created by a specific user
     */
    Group findFirstByCreatedByUserOrderByIdDesc(User createdByUser);

    Group findOneByUid(String uid);

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
    List<Group> findByMembershipsUserAndActive(User user, boolean active);
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
    Couple of methods to be able to discover groups, as long as those have opted in
     */
    List<Group> findByGroupNameContainingIgnoreCaseAndDiscoverable(String nameFragment, boolean discoverable);

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
    
    /* find a group by id and return it and all it's subgroups
    .N.B. when adding columns to the table they must be added here is well
     */
    @Query(value = "WITH RECURSIVE tree(id, created_date_time, name, group_token_code, token_code_expiry, created_by_user, parent, version, reminderminutes) AS ( SELECT pg.* FROM group_profile pg WHERE pg.id = ?1 UNION ALL SELECT sg.* FROM group_profile sg, tree as nodes WHERE sg.parent = nodes.id ) SELECT * FROM tree",nativeQuery = true)
    List<Group> findGroupAndSubGroupsById(Long groupId);

    @Query(value = "with distinct_root as (select distinct q1.root, q1.id as member from (select g.id, getroot(g.id) as root from group_profile g, group_user_membership gu where gu.user_id = ?1 and gu.group_id = g.id  ) as q1) select distinct (getchildren(root)).*, root  from distinct_root order by root,parent", nativeQuery = true)
    List<Object[]> getGroupMemberTree(Long userId);

    @Query(value = "select id, created_date_time, name from getusergroups(?1) where active=true order by maximum_time desc NULLS LAST",nativeQuery = true)
    List<Group> findActiveUserGroupsOrderedByRecentActivity(Long userId);

    @Query(value = "Select * from getusergroupswithsize(?1) where active = true", nativeQuery = true)
    List<Object[]> findActiveUserGroupsOrderedByRecentEvent(Long userId);

    @Query(value = "SELECT g FROM Group g WHERE g.id IN (SELECT gl.groupId FROM GroupLog gl WHERE (gl.createdDateTime BETWEEN ?1 AND ?2) AND gl.groupLogType = 16)")
    List<Group> findGroupsWhereJoinCodeUsedBetween(Instant periodStart, Instant periodEnd);


}
