package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.LogBookDTO;
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
        return logBookRepository.countReplicatedEntries(logBook.getGroup().getId(), logBook.getMessage(), logBook.getCreatedDateTime()) != 0;
    }

    @Override
    public List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook) {
        return logBookRepository.findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(logBook.getGroup().getId(), logBook.getMessage(),
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

    // method called from front end to save a (mostly) fully formed entry
    @Override
    public LogBook create(LogBook logBookToSave, boolean replicateToSubGroups) {
        // todo: proper checks and validation
        if (!replicateToSubGroups)
            return createLogBookEntry(logBookToSave);
        else
            return createLogBookEntryReplicate(logBookToSave);
    }

    @Override
    public LogBook setDueDate(Long logBookId, LocalDateTime actionByDateTime) {
        LogBook logBook = load(logBookId);
        logBook.setActionByDate(Timestamp.valueOf(actionByDateTime));
        return logBookRepository.save(logBook);
    }

    @Override
    public LogBook setAssignedToUser(Long logBookId, Long assignedToUserId) {
        LogBook logBook = load(logBookId);
        User user = userManagementService.loadUser(assignedToUserId);
        logBook.setAssignedToUser(user);
        return logBookRepository.save(logBook);
    }

    @Override
    public boolean isAssignedToUser(Long logBookId) {
        return isAssignedToUser(load(logBookId));
    }

    @Override
    public boolean isAssignedToUser(LogBook logBook) {
        return logBook.getAssignedToUser() != null;
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

    private LogBook createLogBookEntryReplicate(User createdByUser, Group group, String message, Timestamp actionByDate,
                                                User assignedToUser, int reminderMinutes,
                                                int numberOfRemindersLeftToSend) {

        log.info("createLogBookEntryReplicate...parentGroup..." + group);

        // note: when we search on replicatedGroupId we want only the replicated entries, i.e., not
        LogBook parentLogBook = createLogBookEntry(createdByUser, group, message, actionByDate, assignedToUser,
                                                   null, reminderMinutes, numberOfRemindersLeftToSend);
        Timestamp commonCreatedDateTime = parentLogBook.getCreatedDateTime(); // so nanoseconds don't throw later queries

        // note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
        for (Group subgroup : groupManagementService.findGroupAndSubGroupsById(group.getId())) {
            log.info("createLogBookEntryReplicate...groupid..." + subgroup.getId() + "...parentGroup..." + group);
            if (!subgroup.equals(group)) {
                LogBook lb = createReplicatedLogBookEntry(createdByUser, commonCreatedDateTime, subgroup, group, message,
                                                          actionByDate, assignedToUser, reminderMinutes, numberOfRemindersLeftToSend);
            }
        }

        return parentLogBook;
    }

    // helper methods for signature simplification
    private LogBook createLogBookEntryReplicate(LogBook logBook) {
        return createLogBookEntryReplicate(logBook.getCreatedByUser(), logBook.getGroup(), logBook.getMessage(),
                                           logBook.getActionByDate(), logBook.getAssignedToUser(), logBook.getReminderMinutes(),
                                           logBook.getNumberOfRemindersLeftToSend());
    }

    private LogBook createLogBookEntry(LogBook logBook) {
        return createLogBookEntry(logBook.getCreatedByUser(), logBook.getGroup(), logBook.getMessage(), logBook.getActionByDate(),
                                  logBook.getAssignedToUser(), logBook.getReplicatedGroup(), logBook.getReminderMinutes(),
                                  logBook.getNumberOfRemindersLeftToSend());
    }

    /*
    If no reminders are to be sent then set the numberOfRemindersLeftToSend field to a negative value.
    If it is 0 it will be defaulted to 3
     */
    private LogBook createLogBookEntry(User createdByUser, Group group, String message, Timestamp actionByDate,
                                       User assignedToUser, Group replicatedGroup, int reminderMinutes,
                                       int numberOfRemindersLeftToSend) {
        LogBook logBook = new LogBook(createdByUser, group, replicatedGroup, message, actionByDate, assignedToUser, reminderMinutes);
        if (numberOfRemindersLeftToSend == 0) {
            numberOfRemindersLeftToSend = 3; // todo: replace with a logic based on group paid / not paid
        }
        logBook.setNumberOfRemindersLeftToSend(numberOfRemindersLeftToSend);

        LogBook savedLogbook = logBookRepository.save(logBook);
        jmsTemplateProducerService.sendWithNoReply("new-logbook",new LogBookDTO(savedLogbook));
        return  savedLogbook;
    }

    private LogBook createReplicatedLogBookEntry(User createdByUser, Timestamp commonCreatedDateTime, Group group,
                                                 Group replicatedGroup, String message, Timestamp actionByDate, User assignedToUser,
                                                 int reminderMinutes, int numberOfRemindersLeftToSend) {
        LogBook logBook = new LogBook(createdByUser, group, replicatedGroup, message, actionByDate, assignedToUser, reminderMinutes);
        if (numberOfRemindersLeftToSend == 0) {
            numberOfRemindersLeftToSend = 3;
        }
        logBook.setNumberOfRemindersLeftToSend(numberOfRemindersLeftToSend);
        LogBook savedLogbook = logBookRepository.save(logBook);
        jmsTemplateProducerService.sendWithNoReply("new-logbook",new LogBookDTO(savedLogbook));
        return  savedLogbook;
    }

}
