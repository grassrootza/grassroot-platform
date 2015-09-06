package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface GroupRepository extends PagingAndSortingRepository<Group, Long> {
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
    // Page<Group> findByGroupMember(User sessionUser, Pageable pageable);
}
