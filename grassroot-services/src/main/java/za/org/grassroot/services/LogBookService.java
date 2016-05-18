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

    List<LogBook> getLogBookEntriesInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<LogBook> getAllLogBookEntriesForGroup(Group group, boolean completed);

    boolean hasReplicatedEntries(LogBook logBook);

    List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook);

    LogBook getParentLogBookEntry(LogBook logBook);

    /*
    Methods for setting properties
     */

    LogBook save(LogBook logBook);
}
