package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.LogBook;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookService {

    LogBook load(Long logBookId);

    List<LogBook> getAllLogBookEntriesForGroup(Long groupId);

    List<LogBook> getLogBookEntriesInPeriod(Long groupId, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<LogBook> getAllLogBookEntriesForGroup(Long groupId, boolean completed);

    Page<LogBook> getAllLogBookEntriesForGroup(Long groupId, int pageNumber,int pageSize, boolean completed);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId, boolean completed);

    List<LogBook> getAllReplicatedEntriesForGroupAndMessage(Long groupId, String message);

    boolean hasReplicatedEntries(LogBook logBook);

    List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook);

    boolean hasParentLogBookEntry(LogBook logBook);

    LogBook getParentLogBookEntry(LogBook logBook);

    /*
    Methods for setting properties
     */

    LogBook setDueDate(Long logBookId, LocalDateTime actionByDateTime);

    LogBook setMessage(Long logBookId, String message);

    LogBook save(LogBook logBook);

    /*
    Set of methods to deal with marking complete, depending on what has been specified in the view layer
     */

    LogBook setCompleted(Long logBookId, Long completedByUserId);

    LogBook setCompletedWithDate(Long logBookId, Long completedByUserId, LocalDateTime completedDate);

    LogBook setCompleted(Long logBookId, LocalDateTime completedDate);

    LogBook setCompleted(Long logBookId);
}
