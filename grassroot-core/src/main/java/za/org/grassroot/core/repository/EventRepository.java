package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface EventRepository extends CrudRepository<Event, Long> {

    /*
    Find all the groups created by a specific user
     */
    List<Event> findByCreatedByUser(User createdByUser);
    /*
    Find the last group created by a specific user
     */
    Event findFirstByCreatedByUserOrderByIdDesc(User createdByUser);


    List<Event> findByAppliesToGroup(Group group);

}
