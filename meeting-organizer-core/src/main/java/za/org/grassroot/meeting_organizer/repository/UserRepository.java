package za.org.grassroot.meeting_organizer.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.meeting_organizer.domain.User;

import java.util.List;

public interface UserRepository extends CrudRepository<User, Integer> {

    List<User> findByPhoneNumber(String phoneNumber);

}
