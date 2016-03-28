package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.LogBook;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookRepository extends JpaRepository<LogBook, Long> {

    LogBook findOneByUid(String uid);

    List<LogBook> findAllByGroupId(Long groupId);
    List<LogBook> findAllByGroupIdAndCreatedDateTimeBetween(Long groupId, Timestamp start, Timestamp end, Sort sort);
    List<LogBook> findByGroupIdAndMessageAndCreatedDateTime(Long groupId, String message, Timestamp createdDateTime);

    List<LogBook> findAllByGroupIdAndCompletedAndActionByDateGreaterThan(Long groupId, boolean completed, Timestamp dueDate);
    Page<LogBook> findAllByGroupIdAndCompletedAndActionByDateGreaterThan(Long groupId, Pageable pageable, boolean completed, Timestamp dueDate);

    Page<LogBook> findByGroupUidAndCompletedOrderByActionByDateDesc(String groupUid, boolean completed, Pageable pageable);

    Page<LogBook> findAll(Pageable pageable);

    List<LogBook> findAllByAssignedMembersId(Long assignToUserId);
    List<LogBook> findAllByAssignedMembersIdAndCompleted(Long assignToUserId, boolean completed);

    List<LogBook> findAllByReplicatedGroupId(Long replicatedGroupId);
    List<LogBook> findAllByReplicatedGroupIdAndCompleted(Long replicatedGroupId, boolean completed);
    List<LogBook> findAllByReplicatedGroupIdAndMessageOrderByGroupIdAsc(Long replicatedGroupId, String message);
    List<LogBook> findAllByReplicatedGroupIdAndMessageAndActionByDateOrderByGroupIdAsc(Long replicatedGroupId, String message, Timestamp actionByDateTime);

    // methods for analyzing logbooks (for admin)
    Long countByCreatedDateTimeBetween(Timestamp start, Timestamp end);

    @Query(value = "select * from log_book l where l.action_by_date is not null and l.completed = false and l.number_of_reminders_left_to_send > 0 and (l.action_by_date + l.reminder_minutes * INTERVAL '1 minute') < current_timestamp", nativeQuery = true)
    List<LogBook> findLogBookReminders();

    @Query(value = "select count(*) from log_book l where l.replicated_group_id=?1 and l.message=?2 and l.action_by_date=?3", nativeQuery = true)
    int countReplicatedEntries(Long groupId, String message, Timestamp actionByDate);

}
