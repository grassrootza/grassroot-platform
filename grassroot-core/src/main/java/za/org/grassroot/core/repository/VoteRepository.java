package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Vote;

import java.time.Instant;
import java.util.List;

public interface VoteRepository extends JpaRepository<Vote, Long>, JpaSpecificationExecutor<Event> {

	Vote findOneByUid(String uid);
	int countByCreatedDateTimeBetween(Instant start, Instant end);
	int countByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(User user, Instant start, Instant end);

	Page<Vote> findByParentGroupMembershipsUserAndEventStartDateTimeBetweenAndCanceledFalseOrderByEventStartDateTimeDesc(User user, Instant startTime, Instant endTime, Pageable page);
	List<Vote> findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(Group group, Instant startDateTime, Instant endDateTime);

	@Transactional
	@Query(value = "SELECT e FROM Event e " +
			"WHERE e.class = 'VOTE' AND " +
			"(e.eventStartDateTime between ?1 and ?2) " +
			"AND e.canceled = FALSE " +
			"AND (SELECT count(el) FROM EventLog el WHERE el.eventLogType = za.org.grassroot.core.enums.EventLogType.RESULT AND e = el.event) = 0")
	List<Vote> findUnsentVoteResults(Instant intervalStart, Instant intervalEnd);


}
