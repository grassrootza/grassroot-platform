package za.org.grassroot.services;

import org.hibernate.jpa.criteria.expression.UnaryOperatorExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 12/5/15.
 */
@Component
public class LogBookManager implements LogBookService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    /*
        Minus value will send a reminder before actionByDate, Plus value will send a reminder x minutes after
        actionByDate.
    */

    private static final int defaultReminderMinutes = -1440;

    @Autowired
    private LogBookRepository logBookRepository;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Override
    public LogBook load(Long logBookId) {
        return logBookRepository.findOne(logBookId);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Long groupId) {
        return logBookRepository.findAllByGroupId(groupId);
    }

    @Override
    public List<LogBook> getLogBookEntriesInPeriod(Long groupId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Sort sort = new Sort(Sort.Direction.ASC, "CreatedDateTime");
        return logBookRepository.findAllByGroupIdAndCreatedDateTimeBetween(groupId, Timestamp.valueOf(periodStart),
                                                                                      Timestamp.valueOf(periodEnd), sort);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Long groupId, boolean completed) {
        // use an old timestamp both so we prune the really old entries, and to get around half-formed ("null due date") entries
        log.info("Getting all logBook entries for groupId ... " + groupId + " ... and completed state: " + completed);
        return logBookRepository.findAllByGroupIdAndCompletedAndActionByDateGreaterThan(
                groupId, completed, Timestamp.valueOf(LocalDateTime.now().minusYears(1L)));
    }

    @Override
    public Page<LogBook> getAllLogBookEntriesForGroup(Long groupId, int pageNumber, int pageSize, boolean completed) {
        return logBookRepository.findAllByGroupIdAndCompletedAndActionByDateGreaterThan(groupId,
                new PageRequest(pageNumber,pageSize),completed,
                Timestamp.valueOf(LocalDateTime.now().minusYears(1L)));
    }


    @Override
    public List<LogBook> getAllReplicatedEntriesForGroup(Long groupId) {
        return logBookRepository.findAllByReplicatedGroupId(groupId);
    }

    @Override
    public List<LogBook> getAllReplicatedEntriesForGroup(Long groupId, boolean completed) {
        return logBookRepository.findAllByReplicatedGroupIdAndCompleted(groupId, completed);
    }

    @Override
    public List<LogBook> getAllReplicatedEntriesForGroupAndMessage(Long groupId, String message) {
        return logBookRepository.findAllByReplicatedGroupIdAndMessage(groupId, message);
    }

    @Override
    public boolean hasReplicatedEntries(LogBook logBook) {
        return logBookRepository.countReplicatedEntries(logBook.resolveGroup().getId(), logBook.getMessage(), logBook.getCreatedDateTime()) != 0;
    }

    @Override
    public List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook) {
        return logBookRepository.findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(logBook.resolveGroup().getId(), logBook.getMessage(),
                                                                                                         logBook.getCreatedDateTime());
    }

    @Override
    public boolean hasParentLogBookEntry(LogBook logBook) {
        return logBook.getReplicatedGroup() != null;
    }

    @Override
    public LogBook getParentLogBookEntry(LogBook logBook) {
        // todo error handling just in case something went wrong on insert and the repository call comes back empty
        Group parentLogBookGroupId = logBook.getReplicatedGroup();
        if (parentLogBookGroupId == null) {
            return null;
        }
        else return logBookRepository.findByGroupIdAndMessageAndCreatedDateTime(parentLogBookGroupId.getId(), logBook.getMessage(),
                                                                                logBook.getCreatedDateTime()).get(0);
    }

    @Override
    public LogBook setDueDate(Long logBookId, LocalDateTime actionByDateTime) {
        LogBook logBook = load(logBookId);
        logBook.setActionByDate(Timestamp.valueOf(actionByDateTime));
        return logBookRepository.save(logBook);
    }

    @Override
    public LogBook setMessage(Long logBookId, String message) {
        LogBook logBook = load(logBookId);
        logBook.setMessage(message);
        return logBookRepository.save(logBook);
    }

    @Override
    public LogBook save(LogBook logBook) {
        // note: may want to put logic into this like eventsManager .. at present only one view uses it, so may be okay
        return logBookRepository.save(logBook);
    }

    // todo: tidy these up on a refactor, there's quite a bit of redundancy
    @Override
    public LogBook setCompleted(Long logBookId, Long completedByUserId) {
        return setCompletedWithDate(logBookId, completedByUserId, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    public LogBook setCompleted(Long logBookId, Long completedByUserId, String completedDate) {
        if (completedDate != null) {
            Timestamp timestamp = Timestamp.valueOf(LocalDate.parse(completedDate, DateTimeUtil.preferredDateFormat).atStartOfDay());
            return (completedByUserId == null) ? setCompleted(logBookId, timestamp) :
                    setCompletedWithDate(logBookId, completedByUserId, timestamp);
        } else {
            return (completedByUserId == null) ? setCompleted(logBookId) : setCompleted(logBookId, completedByUserId);
        }
    }

    @Override
    public LogBook setCompletedWithDate(Long logBookId, Long completedByUserId, Timestamp completedDate) {
        return setCompleted(logBookRepository.findOne(logBookId), completedByUserId, completedDate);
    }

    @Override
    public LogBook setCompleted(Long logBookId) {
        return setCompleted(logBookId, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    public LogBook setCompleted(Long logBookId, Timestamp completedDate) {
        // if no user assigned, then logBook.getId() returns null, which is set as completed user, which is as wanted
        LogBook logBook = logBookRepository.findOne(logBookId);
        return setCompleted(logBook, 0L, completedDate);
    }

    private LogBook setCompleted(LogBook logBook, Long completedByUserId, Timestamp completedDate) {
        // todo: checks to make sure not already completed, possibly also do a check for permissions here
        logBook.setCompleted(true);
        // todo: redesign to newer LogBookbroker, or change this somehow
//        logBook.setCompletedByUserId(completedByUserId);
        logBook.setCompletedDate(completedDate);
        return logBookRepository.save(logBook);
    }
}
