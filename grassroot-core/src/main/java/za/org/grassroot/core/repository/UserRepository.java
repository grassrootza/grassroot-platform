package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.task.Event;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /*
    Since phoneNumbers are unique, replacing the prior method, which returned a list of Users, with this, for efficiency
    Note: can now no longer rely on NoSuchElement exceptions to catch 'no such user', probably should now do ourselves
     */
    User findByPhoneNumberAndPhoneNumberNotNull(String phoneNumber);

    User findByEmailAddressAndEmailAddressNotNull(String emailAddress);

    User findOneByUid(String uid);

    List<User> findByUidIn(Set<String> uids);

    /*
    Used in admin pages to find users who can then be designated, modified, etc. Probably want a better search method
    than to use 'containing', but will do for now.
    Note: we can possibly also use something like to do an auto-complete or dropdown in group creation
     */
    List<User> findByPhoneNumberContaining(String phoneNumber);
    List<User> findByDisplayNameContaining(String displayName);
    List<User> findByDisplayNameContainingOrPhoneNumberContaining(String userInput, String phoneNumber);

    @Query("select u from Membership m " +
            "inner join m.user u " +
            "inner join m.group g " +
            "where g = :group and (u.displayName like concat('%', :userInput ,'%') or u.phoneNumber like concat('%', :phoneNumber ,'%'))")
    List<User> findByGroupsPartOfAndDisplayNameContainingOrPhoneNumberContaining(
            @Param("group") Group group, @Param("userInput") String userInput, @Param("phoneNumber") String phoneNumber);

    @Query("select u from Membership m " +
            "inner join m.user u " +
            "inner join m.group g " +
            "where g = :group and (upper(u.displayName) like concat('%', upper(:userInput) ,'%') or u.phoneNumber like :phoneNumber)")
    List<User> findByGroupsPartOfAndDisplayNameContainingIgnoreCaseOrPhoneNumberLike(
            @Param("group") Group group, @Param("userInput") String userInput, @Param("phoneNumber") String phoneNumber);

    @Query("select u from Membership m " +
            "inner join m.user u " +
            "inner join m.group g " +
            "where g = :group and u.id is not :excludedUserId")
    List<User> findByGroupsPartOfAndIdNot(@Param("group") Group group, @Param("excludedUserId") Long excludedUserId);

    User findByUsername(String username);

    /*
    See if the phone number exists, before adding it
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN 'true' ELSE 'false' END FROM User u WHERE u.phoneNumber = ?1")
    Boolean existsByPhoneNumber(String phoneNumber);

    @Query("SELECT CASE WHEN COUNT(u) > 0 then 'true' ELSE 'false' END FROM User u where lower(u.emailAddress) = lower(?1)")
    Boolean existsByEmail(String emailAddress);

    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e " +
            "and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.RSVP and el.response = 'YES'")
    List<User> findUsersThatRSVPYesForEvent(Event event);

    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e " +
            "and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.RSVP and el.response = 'NO'")
    List<User> findUsersThatRSVPNoForEvent(Event event);

    @Query("select u from User u, EventLog el, Event e where e = ?1 and el.event = e and u = el.user and el.eventLogType = za.org.grassroot.core.enums.EventLogType.RSVP")
    List<User> findUsersThatRSVPForEvent(Event event);

    @Query("select t from Notification n " +
            "inner join n.target t inner join n.event e " +
            "where e = ?1 and type(n) = ?2")
    List<User> findNotificationTargetsForEvent(Event event, Class<? extends EventNotification> notificationClass);

    List<User> findByPhoneNumberIn(Collection<String> phoneNumbers);
    List<User> findByEmailAddressIn(Collection<String> emailAddresses);

    @Query("select lower(u.emailAddress) from User u where u.enabled = true")
    Set<String> fetchUsedEmailAddresses();

    @Query("select u.phoneNumber from User u where u.enabled = true")
    Set<String> fetchUserPhoneNumbers();

}
