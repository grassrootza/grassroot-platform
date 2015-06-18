package za.org.grassroot.meeting_organizer.service.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.meeting_organizer.model.User;

public interface UserRepository extends CrudRepository<User, Integer> {
}
