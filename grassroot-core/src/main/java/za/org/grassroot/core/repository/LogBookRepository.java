package za.org.grassroot.core.repository;

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
    List<LogBook> findAllByGroupIdAndCompleted(Long groupId, boolean completed);
    List<LogBook> findAllByGroupIdAndCompletedAndActionByDateGreaterThan(Long groupId, boolean completed, Timestamp dueDate);

    List<LogBook> findAllByAssignedToUserId(Long assignToUserId);
    List<LogBook> findAllByAssignedToUserIdAndCompleted(Long assignToUserId, boolean completed);
    List<LogBook> findAllByReplicatedGroupId(Long replicatedGroupId);
    List<LogBook> findAllByReplicatedGroupIdAndCompleted(Long replicatedGroupId, boolean completed);

    @Query(value = "select * from log_book l where l.action_by_date is not null and l.completed = false and l.number_of_reminders_left_to_send > 0 and (l.action_by_date + l.reminder_minutes * INTERVAL '1 minute') < current_timestamp", nativeQuery = true)
    List<LogBook> findLogBookReminders();

}
