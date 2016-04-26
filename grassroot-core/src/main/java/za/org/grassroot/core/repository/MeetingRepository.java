package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

	Meeting findOneByUid(String uid);

    int countByCreatedDateTimeBetween(Instant start, Instant end);

	List<Meeting> findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(Instant startTimeAfter, Instant startTimeBefore);

    /**
     * Find meetings for which we should send out response totals (i.e., X said yes, and so forth), being meetings:
     * Called in a certain interval of time
     * Not cancelled
     * With their start date time in the future (after the Instant, passed to get around weird H2/current_timestamp/Instant conversion thing
     * @return
     */
    @Query(value = "SELECT e FROM Event e WHERE e.eventStartDateTime > ?1 AND e.class = 'MEETING' AND e.canceled = FALSE AND (e.createdDateTime BETWEEN ?2 AND ?3) AND (SELECT count(el) FROM EventLog el WHERE el.eventLogType = 10 AND e = el.event) = 0 ")
    List<Meeting> meetingsForResponseTotals(Instant startTimeAfter, Instant intervalStart, Instant intervalEnd);

    /*
    @Query(value = "SELECT * FROM event e WHERE start_date_time  between  (current_timestamp - INTERVAL '1 hour') and current_timestamp AND e.type = 'VOTE' AND e.canceled = FALSE AND (SELECT count(*) FROM event_log el WHERE el.event_log_type = 7 AND e.id = el.event_id) = 0", nativeQuery = true)
	List<Vote> findUnsentVoteResults();
     */

	Page<Meeting> findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled, Pageable page);

	Page<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(User user, Instant startTime, boolean cancelled, Pageable page);

	Page<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(User user, Instant startTime, boolean cancelled, Pageable page);

	List<Meeting> findByAppliesToGroupAndEventStartDateTimeBetween(Group group, Instant startDateTime, Instant endDateTime);

	List<Meeting> findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled);
	List<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Instant startTime, boolean cancelled);
	List<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceled(User user, Instant startTime, boolean cancelled);

}
