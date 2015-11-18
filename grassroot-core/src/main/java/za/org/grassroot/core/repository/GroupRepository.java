package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.Date;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    /*
    Find all the groups created by a specific user
     */
    List<Group> findByCreatedByUser(User createdByUser);
    /*
    Find the last group created by a specific user
     */
    Group findFirstByCreatedByUserOrderByIdDesc(User createdByUser);
    /*
    Get the sub-groups for a specific group
    one level only
     */
    List<Group> findByParent(Group parent);
    /*
    Find all the groups that a user is part of, with pagination
     */
    List<Group> findByGroupMembers(User sessionUser);
    Page<Group> findByGroupMembers(User sessionUser, Pageable pageable);
    /*
    Find groups which are active
     */
    List<Group> findByGroupMembersAndActive(User user, boolean active);
    Page<Group> findByGroupMembersAndActive(User user, Pageable pageable, boolean active);
    /*
    Find a group by a code
     */
    Group findByGroupTokenCode(String groupTokenCode);
    Group findByGroupTokenCodeAndTokenExpiryDateTimeAfter(String groupTokenCode, Date expiryTime);

    /*
    Find all groups, with pagination--for system admin
     */
    Page<Group> findAll(Pageable pageable);

    /*
    Find the max(groupTokenCode) in table
    N.B. remove this when we stop using integer values
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(group_token_code as INTEGER)),123) FROM group_profile g", nativeQuery = true)
    int getMaxTokenValue();
    
    /* find a group by id and return it and all it's subgroups
    .N.B. when adding columns to the table they must be added here is well
     */
    @Query(value = "WITH RECURSIVE tree(id, created_date_time, name, group_token_code, token_code_expiry, created_by_user, parent, version, reminderminutes) AS ( SELECT pg.* FROM group_profile pg WHERE pg.id = ?1 UNION ALL SELECT sg.* FROM group_profile sg, tree as nodes WHERE sg.parent = nodes.id ) SELECT * FROM tree",nativeQuery = true)
    List<Group> findGroupAndSubGroupsById(Long groupId);
}
