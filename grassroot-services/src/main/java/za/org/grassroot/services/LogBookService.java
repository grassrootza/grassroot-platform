package za.org.grassroot.services;

import za.org.grassroot.core.domain.LogBook;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookService {

    LogBook load(Long logBookId);

    List<LogBook> getAllLogBookEntriesForGroup(Long groupId);

    List<LogBook> getAllLogBookEntriesForGroup(Long groupId, boolean completed);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId, boolean completed);

    LogBook create(Long createdByUserId, Long groupId, String message);

    LogBook create(Long createdByUserId, Long groupId, String message, Timestamp actionByDate);

    LogBook create(Long createdByUserId, Long groupId, String message, Timestamp actionByDate, Long assignToUserId);

    LogBook create(Long createdByUserId, Long groupId, String message, boolean replicateToSubGroups);

    LogBook create(LogBook logBookToSave, boolean replicateToSubGroups);

    LogBook setDueDate(Long logBookId, LocalDateTime actionByDateTime);

    LogBook setAssignedToUser(Long logBookId, Long assignedToUserId);

    boolean isAssignedToUser(Long logBookId);

    boolean isAssignedToUser(LogBook logBook);

    LogBook setMessage(Long logBookId, String message);

    /*
    Set of methods to deal with marking complete, depending on what has been specified in the view layer
     */

    LogBook setCompleted(Long logBookId, Long completedByUserId);

    LogBook setCompletedWithDate(Long logBookId, Long completedByUserId, Timestamp completedDate);

    LogBook setCompleted(Long logBookId, Timestamp completedDate);

    LogBook setCompleted(Long logBookId);
}
