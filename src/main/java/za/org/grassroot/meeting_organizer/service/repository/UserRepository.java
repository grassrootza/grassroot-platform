package za.org.grassroot.meeting_organizer.service.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.meeting_organizer.model.User;

import java.util.List;

public interface UserRepository extends CrudRepository<User, Integer> {

    List<User> findByPhoneNumber(String phoneNumber);

}
