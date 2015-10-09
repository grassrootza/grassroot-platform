package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.util.Date;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    /*
    Find all the groups created by a specific user
     */
    List<Event> findByCreatedByUser(User createdByUser);
    /*
    Find the last group created by a specific user
     */
    Event findFirstByCreatedByUserOrderByIdDesc(User createdByUser);


    List<Event> findByAppliesToGroup(Group group);

    List<Event> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(Group group, Date startTime, boolean cancelled);

    List<Event> findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled);

    /*

    N.B. do not remove start_date_time > current_timestamp as it will force the query to do an
    index scan, when there is enough data

    select * from event e
        where e.canceled = FALSE
        and start_date_time > current_timestamp -- index for start_date_time and so we can read by index - local timestamp???
        and (start_date_time - INTERVAL '30 minute') > current_timestamp --minimum notification period
        and date(start_date_time) <= (current_date + interval '1 day') -- local date??? - 1 day interval to catch meetings after midnight


     */
    @Query(value = "select * from event e where e.canceled = FALSE and start_date_time > current_timestamp and (start_date_time - INTERVAL '?1 minute') > current_timestamp and date(start_date_time) <= (current_date + interval '1 day')",nativeQuery = true)
    List<Event> findEventsForReminders(int minimumReminderMinutes);
}
