package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;

import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

    /*
    Since phoneNumbers are unique, replacing the prior method, which returned a list of Users, with this, for efficiency
    Note: can now no longer rely on NoSuchElement exceptions to catch 'no such user', probably should now do ourselves
     */
    User findByPhoneNumber(String phoneNumber);

    User findOneByUid(String uid);

    /*
    Used in admin pages to find users who can then be designated, modified, etc. Probably want a better search method
    than to use 'containing', but will do for now.
    Note: we can possibly also use something like to do an auto-complete or dropdown in group creation
     */
    List<User> findByPhoneNumberContaining(String phoneNumber);
    List<User> findByDisplayNameContaining(String displayName);
    List<User> findByDisplayNameContainingOrPhoneNumberContaining(String userInput, String phoneNumber);

    List<User> findByGroupsPartOfOrderByIdAsc(Group group);
    List<User> findByGroupsPartOfAndDisplayNameContainingOrPhoneNumberContaining(Group group, String userInput, String phoneNumber);
    List<User> findByGroupsPartOfAndDisplayNameContainingIgnoreCaseOrPhoneNumberLike(Group group, String userInput, String phoneNumber);

    User findByUsername(String username);

    int countByCreatedDateTimeBetween(Timestamp start, Timestamp end);
    List<User> findByCreatedDateTimeBetweenOrderByCreatedDateTimeDesc(Timestamp start, Timestamp end);

    int countByHasInitiatedSession(boolean hasInitiatedSession);
    int countByCreatedDateTimeBetweenAndHasInitiatedSession(Timestamp start, Timestamp end, boolean hasInitiatedSession);
    List<User> findByCreatedDateTimeBetweenAndHasInitiatedSession(Timestamp start, Timestamp end, boolean hasInitiatedSession);

    int countByHasWebProfile(boolean webProfile);
    int countByCreatedDateTimeBetweenAndHasWebProfile(Timestamp start, Timestamp end, boolean webProfile);
    List<User> findByCreatedDateTimeBetweenAndHasWebProfile(Timestamp start, Timestamp end, boolean hasWebProfile);

    /*
    See if the phone number exists, before adding it
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN 'true' ELSE 'false' END FROM User u WHERE u.phoneNumber = ?1")
    Boolean existsByPhoneNumber(String phoneNumber);

    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.EventRSVP and el.message = 'Yes'")
    List<User> findUsersThatRSVPYesForEvent(Event event);

    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.EventRSVP and el.message = 'No'")
    List<User> findUsersThatRSVPNoForEvent(Event event);

    @Query(value = "select u from User u where u.phoneNumber IN :phone_numbers")
    List<User> findExistingUsers(@Param("phone_numbers") List<String> numbers);

    List<User> findByGroupsPartOfAndIdNot(Group group, Long excludedUserId);

    @Query(value = "select id, display_name,phone_number,language_code from user_profile where user_profile.phone_number =?1", nativeQuery = true)
    Object[] findByNumber(String phoneNumber);

}
