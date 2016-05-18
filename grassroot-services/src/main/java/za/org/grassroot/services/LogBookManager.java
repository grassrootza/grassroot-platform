package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.repository.LogBookRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 12/5/15.
 */
@Component
public class LogBookManager implements LogBookService {

    @Autowired
    private LogBookRepository logBookRepository;

    @Override
    public List<LogBook> getLogBookEntriesInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Sort sort = new Sort(Sort.Direction.ASC, "createdDateTime");
        Instant start = convertToSystemTime(periodStart, getSAST());
        Instant end = convertToSystemTime(periodEnd, getSAST());
        return logBookRepository.findByParentGroupAndCreatedDateTimeBetween(group, start, end, sort);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Group group, boolean completed) {
        // use an old timestamp both so we prune the really old entries, and to get around half-formed ("null due date") entries
        return logBookRepository.findByParentGroupAndCompletedAndActionByDateGreaterThan(
                group, completed, LocalDateTime.now().minusYears(1L).toInstant(ZoneOffset.UTC));
    }

    @Override
    public boolean hasReplicatedEntries(LogBook logBook) {
        return logBookRepository.countReplicatedEntries(logBook.getAncestorGroup(), logBook.getMessage(), logBook.getCreatedDateTime()) != 0;
    }

    @Override
    public List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook) {
        return logBookRepository.findByReplicatedGroupAndMessageAndActionByDateOrderByParentGroupIdAsc(logBook.getAncestorGroup(), logBook.getMessage(),
                                                                                                 logBook.getActionByDate());
    }

    @Override
    public LogBook getParentLogBookEntry(LogBook logBook) {
        // todo error handling just in case something went wrong on insert and the repository call comes back empty
        Group parentLogBookGroup = logBook.getReplicatedGroup();
        if (parentLogBookGroup == null) {
            return null;
        }
        else return logBookRepository.findByParentGroupAndMessageAndCreatedDateTime(parentLogBookGroup, logBook.getMessage(),
                                                                              logBook.getCreatedDateTime()).get(0);
    }

    @Override
    public LogBook save(LogBook logBook) {
        // note: may want to put logic into this like eventsManager .. at present only one view uses it, so may be okay
        return logBookRepository.save(logBook);
    }
}
