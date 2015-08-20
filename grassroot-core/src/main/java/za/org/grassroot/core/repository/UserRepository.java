package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByPhoneNumber(String phoneNumber);

    List<User> findByUsername(String username);
    /*
    See if the phone number exists, before adding it
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN 'true' ELSE 'false' END FROM User u WHERE u.phoneNumber = ?1")
    public Boolean existsByPhoneNumber(String phoneNumber);


    //TODO get all users linked to a group
    //TODO get all users linked to a group hierarchy, remember to remove duplicates at service level


}
