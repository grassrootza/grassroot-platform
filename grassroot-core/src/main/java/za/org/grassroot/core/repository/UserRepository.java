package za.org.grassroot.core.repository;

import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface UserRepository extends PagingAndSortingRepository<User, Long> {

    List<User> findByPhoneNumber(String phoneNumber);

    List<User> findByUsername(String username);



}
