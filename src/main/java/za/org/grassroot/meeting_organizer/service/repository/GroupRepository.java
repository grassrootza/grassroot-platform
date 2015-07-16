package za.org.grassroot.meeting_organizer.service.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.meeting_organizer.model.Group;

public interface GroupRepository extends CrudRepository<Group, Integer> {
}
