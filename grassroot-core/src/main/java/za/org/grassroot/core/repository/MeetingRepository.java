package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

	Meeting findOneByUid(String uid);

    int countByCreatedDateTimeBetween(Timestamp start, Timestamp end);

	List<Meeting> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(Group group, Date startTime, boolean cancelled);

	Page<Meeting> findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled, Pageable page);

	Page<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(User user, Date startTime, boolean cancelled, Pageable page);

	Page<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(User user, Date startTime, boolean cancelled, Pageable page);

	List<Meeting> findByAppliesToGroupAndEventStartDateTimeBetween(Group group, Timestamp startDateTime, Timestamp endDateTime);

	List<Meeting> findByAppliesToGroupMembershipsUserAndCanceledOrderByEventStartDateTimeDesc(User user, boolean canceled);
	List<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled);
	List<Meeting> findByAppliesToGroupMembershipsUserAndEventStartDateTimeLessThanAndCanceled(User user, Date startTime, boolean cancelled);

}
