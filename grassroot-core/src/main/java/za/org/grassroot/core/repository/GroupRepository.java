package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface GroupRepository extends CrudRepository<Group, Long> {
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
}
