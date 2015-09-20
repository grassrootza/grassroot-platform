package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findByPhoneNumber(String phoneNumber);

    User findByUsername(String username);
    /*
    See if the phone number exists, before adding it
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN 'true' ELSE 'false' END FROM User u WHERE u.phoneNumber = ?1")
    public Boolean existsByPhoneNumber(String phoneNumber);


    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.EventRSVP and el.message = 'Yes'")
    List<User> findUsersThatRSVPYesForEvent(Event event);

    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.EventRSVP and el.message = 'No'")
    List<User> findUsersThatRSVPNoForEvent(Event event);




}
