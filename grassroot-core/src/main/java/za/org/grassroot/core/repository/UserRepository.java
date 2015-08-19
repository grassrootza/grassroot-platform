package za.org.grassroot.core.repository;

import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface UserRepository extends PagingAndSortingRepository<User, Long> {

    List<User> findByPhoneNumber(String phoneNumber);

    List<User> findByUsername(String username);

    //TODO get all users linked to a group
    //TODO get all users linked to a group hierarchy, remember to remove duplicates at service level


}
