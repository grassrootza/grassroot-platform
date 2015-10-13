package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    /*
    Since phoneNumbers are unique, replacing the prior method, which returned a list of Users, with this, for efficiency
    Note: can now no longer rely on NoSuchElement exceptions to catch 'no such user', probably should now do ourselves
     */
    User findByPhoneNumber(String phoneNumber);

    /*
    Used in admin pages to find users who can then be designated, modified, etc. Probably want a better search method
    than to use 'containing', but will do for now.
    Note: we can possibly also use something like to do an auto-complete or dropdown in group creation
     */
    List<User> findByPhoneNumberContaining(String phoneNumber);
    List<User> findByDisplayNameContaining(String displayName);

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
