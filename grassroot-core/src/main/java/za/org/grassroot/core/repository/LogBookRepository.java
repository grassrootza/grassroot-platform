package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.LogBook;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookRepository extends JpaRepository<LogBook, Long> {

    List<LogBook> findAllByGroupId(Long groupId);
    List<LogBook> findAllByGroupIdAndRecorded(Long groupId, boolean recorded);
    List<LogBook> findByGroupIdAndMessageAndCreatedDateTime(Long groupId, String message, Timestamp createdDateTime);

    // note: no way a non-recorded action gets to completion (manager throws an exception), so adding that would be redundant
    List<LogBook> findAllByGroupIdAndCompletedAndRecorded(Long groupId, boolean completed, boolean recorded);
    List<LogBook> findAllByGroupIdAndCompletedAndRecordedAndActionByDateGreaterThan(Long groupId, boolean completed, boolean recorded, Timestamp dueDate);
    Page<LogBook> findAllByGroupIdAndCompletedAndRecordedAndActionByDateGreaterThan(Long groupId, Pageable pageable, boolean completed, boolean recorded, Timestamp dueDate);

    Page<LogBook> findAll(Pageable pageable);

    List<LogBook> findAllByAssignedToUserIdAndRecorded(Long assignToUserId, boolean recorded);
    List<LogBook> findAllByAssignedToUserIdAndRecordedAndCompleted(Long assignToUserId, boolean recorded, boolean completed);

    // similarly to above: replicate group function only possible via web app, hence always recorded as true
    List<LogBook> findAllByReplicatedGroupId(Long replicatedGroupId);
    List<LogBook> findAllByReplicatedGroupIdAndCompleted(Long replicatedGroupId, boolean completed);
    List<LogBook> findAllByReplicatedGroupIdAndMessage(Long replicatedGroupId, String message);
    List<LogBook> findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(Long replicatedGroupId, String message, Timestamp createdDateTime);

    @Query(value = "select * from log_book l where l.action_by_date is not null and l.completed = false and l.recorded = true and l.number_of_reminders_left_to_send > 0 and (l.action_by_date + l.reminder_minutes * INTERVAL '1 minute') < current_timestamp", nativeQuery = true)
    List<LogBook> findLogBookReminders();

    @Query(value = "select count(*) from log_book l where l.replicated_group_id=?1 and l.message=?2 and l.created_date_time=?3", nativeQuery = true)
    int countReplicatedEntries(Long groupId, String message, Timestamp createdDateTime);

}
