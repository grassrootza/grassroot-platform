package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Group;

public interface GroupRepository extends CrudRepository<Group, Long> {
}
