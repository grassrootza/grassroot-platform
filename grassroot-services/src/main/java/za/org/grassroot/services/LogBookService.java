package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookService {

    LogBook load(Long logBookId);

    List<LogBook> getAllLogBookEntriesForGroup(Group group);

    List<LogBook> getLogBookEntriesInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<LogBook> getAllLogBookEntriesForGroup(Group group, boolean completed);

    boolean hasReplicatedEntries(LogBook logBook);

    List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook);

    LogBook getParentLogBookEntry(LogBook logBook);

    /*
    Methods for setting properties
     */

    LogBook save(LogBook logBook);

    /*
    Set of methods to deal with marking complete, depending on what has been specified in the view layer
     */

    LogBook setCompletedWithDate(Long logBookId, Long completedByUserId, LocalDateTime completedDate);

    LogBook setCompleted(Long logBookId, LocalDateTime completedDate);
}
