package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.Vote;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long> {

	Vote findOneByUid(String uid);

	int countByCreatedDateTimeBetween(Timestamp start, Timestamp end);

	@Query(value = "SELECT * FROM event e WHERE start_date_time  between  (current_timestamp - INTERVAL '1 hour') and current_timestamp AND e.type = 'VOTE' AND e.canceled = FALSE AND (SELECT count(*) FROM event_log el WHERE el.event_log_type = 7 AND e.id = el.event_id) = 0", nativeQuery = true)
	List<Vote> findUnsentVoteResults();

	List<Vote> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(Group group, Date startTime, boolean cancelled);

	Page<Vote> findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled, Pageable page);

	Page<Vote> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(User user, Date startTime, boolean cancelled, Pageable page);

	Page<Vote> findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(User user, Date startTime, boolean cancelled, Pageable page);

	List<Vote> findByAppliesToGroupAndEventStartDateTimeBetween(Group group, Timestamp startDateTime, Timestamp endDateTime);

	List<Meeting> findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled);
	List<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled);
	List<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceled(User user, Date startTime, boolean cancelled);
}
