package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long>, JpaSpecificationExecutor<Event> {

	Meeting findOneByUid(String uid);

    int countByCreatedDateTimeBetween(Instant start, Instant end);
	int countByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(User user, Instant start, Instant end);

	@Transactional
	List<Meeting> findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(Instant startTimeAfter, Instant startTimeBefore);

	List<Meeting> findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(Group group, Instant startDateTime, Instant endDateTime);

	Page<Meeting> findByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(User user, Instant startTime, Instant endTime, Pageable page);

    /**
     * Find meetings for which we should send out response totals (i.e., X said yes, and so forth), being meetings:
     * Called in a certain interval of time
     * Not cancelled
     * With their start date time in the future (after the Instant, passed to get around weird H2/current_timestamp/Instant conversion thing
     * @return
     */
    @Transactional
	@Query(value = "SELECT e FROM Event e " +
			"WHERE e.eventStartDateTime > ?1 " +
			"AND e.class = 'MEETING' " +
			"AND e.canceled = FALSE " +
			"AND (e.createdDateTime BETWEEN ?2 AND ?3) " +
			"AND (SELECT count(el) FROM EventLog el WHERE el.eventLogType = za.org.grassroot.core.enums.EventLogType.RSVP_TOTAL_MESSAGE " +
			"AND e = el.event) = 0")
    List<Meeting> meetingsForResponseTotals(Instant startTimeAfter, Instant intervalStart, Instant intervalEnd);


    @Query(value = "SELECT e FROM Event e " +
		"WHERE e.isPublic = TRUE " +
		"AND e.class = 'MEETING' " +
		"AND LOWER(e.name) LIKE LOWER (CONCAT('%', ?1, '%')) " +
        "AND e.ancestorGroup NOT IN(SELECT m.group FROM Membership m WHERE m.user = ?2)")
    List<Meeting> publicMeetingsUserIsNotPartOfWithsSearchTerm(String searchTerm, User user);

}
