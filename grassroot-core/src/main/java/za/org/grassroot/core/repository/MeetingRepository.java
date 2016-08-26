package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

	Meeting findOneByUid(String uid);

    int countByCreatedDateTimeBetween(Instant start, Instant end);

	@Transactional
	List<Meeting> findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(Instant startTimeAfter, Instant startTimeBefore);

	List<Meeting> findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(Group group, Instant startDateTime, Instant endDateTime);

	Meeting findOneByCreatedByUserAndParentGroupAndNameAndEventStartDateTimeBetweenAndCanceledFalse(User user, Group group, String name, Instant startDateTime,
	                                                                                                Instant endDateTime);

    /**
     * Find meetings for which we should send out response totals (i.e., X said yes, and so forth), being meetings:
     * Called in a certain interval of time
     * Not cancelled
     * With their start date time in the future (after the Instant, passed to get around weird H2/current_timestamp/Instant conversion thing
     * @return
     */
    @Transactional
	@Query(value = "SELECT e FROM Event e WHERE e.eventStartDateTime > ?1 AND e.class = 'MEETING' AND e.canceled = FALSE AND (e.createdDateTime BETWEEN ?2 AND ?3) AND (SELECT count(el) FROM EventLog el WHERE el.eventLogType = za.org.grassroot.core.enums.EventLogType.RSVP_TOTAL_MESSAGE AND e = el.event) = 0 ")
    List<Meeting> meetingsForResponseTotals(Instant startTimeAfter, Instant intervalStart, Instant intervalEnd);

	// todo: consider cleaning these up (seems like possible duplication)

	Page<Meeting> findByParentGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled, Pageable page);
	Page<Meeting> findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(User user, Instant startTime, boolean cancelled, Pageable page);
	Page<Meeting> findByParentGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(User user, Instant startTime, boolean cancelled, Pageable page);

	List<Meeting> findByParentGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled);
	List<Meeting> findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Instant startTime, boolean cancelled);
	List<Meeting> findByParentGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceled(User user, Instant startTime, boolean cancelled);

}
