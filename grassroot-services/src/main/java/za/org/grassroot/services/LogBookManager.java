package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.sql.Timestamp;
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
    LogBookRepository logBookRepository;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Override
    public LogBook load(Long logBookId) {
        return logBookRepository.findOne(logBookId);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Long groupId) {
        return logBookRepository.findAllByGroupIdAndRecorded(groupId, true);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Long groupId, boolean completed) {
        // use an old timestamp both so we prune the really old entries, and to get around half-formed ("null due date") entries
        log.info("Getting all logBook entries for groupId ... " + groupId + " ... and completed state: " + completed);
        return logBookRepository.findAllByGroupIdAndCompletedAndRecordedAndActionByDateGreaterThan(
                groupId, completed, true, Timestamp.valueOf(LocalDateTime.now().minusYears(1L)));
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
        return logBookRepository.countReplicatedEntries(logBook.getGroupId(), logBook.getMessage(), logBook.getCreatedDateTime()) != 0;
    }

    @Override
    public List<LogBook> getAllReplicatedEntriesFromParentLogBook(LogBook logBook) {
        return logBookRepository.findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(logBook.getGroupId(), logBook.getMessage(),
                                                                                                         logBook.getCreatedDateTime());
    }

    @Override
    public boolean hasParentLogBookEntry(LogBook logBook) {
        return (logBook.getReplicatedGroupId() != null && logBook.getReplicatedGroupId() != 0);
    }

    @Override
    public LogBook getParentLogBookEntry(LogBook logBook) {
        // todo error handling just in case something went wrong on insert and the repository call comes back empty
        Long parentLogBookGroupId = logBook.getReplicatedGroupId();
        if (parentLogBookGroupId == null || parentLogBookGroupId == 0) return null;
        else return logBookRepository.findByGroupIdAndMessageAndCreatedDateTime(parentLogBookGroupId, logBook.getMessage(),
                                                                                logBook.getCreatedDateTime()).get(0);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message) {
        return createLogBookEntry(createdByUserId, groupId, message, null, 0L, 0L, 0, 0, true);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, boolean recorded) {
        // we only call this from USSD
        return createLogBookEntry(createdByUserId, groupId, null, null, 0L, 0L, 0, 0, false);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message, Timestamp actionByDate) {
        return createLogBookEntry(createdByUserId, groupId, message, actionByDate, 0L, 0L, 0, 0, true);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message, Timestamp actionByDate, Long assignToUserId) {
        return createLogBookEntry(createdByUserId, groupId, message, actionByDate, assignToUserId, 0L, 0, 0, true);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message, boolean replicateToSubGroups) {
        if (replicateToSubGroups) {
            return createLogBookEntryReplicate(createdByUserId, groupId, message, null, 0L, 0, 0);
        } else {
            return createLogBookEntry(createdByUserId, groupId, message, null, 0L, 0L, 0, 0, true);

        }
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
        logBook.setAssignedToUserId(assignedToUserId);
        return logBookRepository.save(logBook);
    }

    @Override
    public boolean isAssignedToUser(Long logBookId) {
        return isAssignedToUser(load(logBookId));
    }

    @Override
    public boolean isAssignedToUser(LogBook logBook) {
        return (logBook.getAssignedToUserId() != null && logBook.getAssignedToUserId() != 0L);
    }

    @Override
    public LogBook setMessage(Long logBookId, String message) {
        LogBook logBook = load(logBookId);
        logBook.setMessage(message);
        return logBookRepository.save(logBook);
    }

    @Override
    public LogBook setRecorded(Long logBookId, boolean recorded) {
        // todo save and check changes akin to message sending for events
        LogBook logBook = load(logBookId);
        logBook.setRecorded(recorded);
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
            Timestamp timestamp = Timestamp.valueOf(LocalDateTime.parse(completedDate, DateTimeUtil.preferredDateFormat));
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
        if (!logBook.isRecorded()) { throw new LogBookUnrecordedException(); }
        logBook.setCompleted(true);
        logBook.setCompletedByUserId(completedByUserId);
        logBook.setCompletedDate(completedDate);
        return logBookRepository.save(logBook);
    }

    private LogBook createLogBookEntryReplicate(Long createdByUserId, Long groupId, String message, Timestamp actionByDate,
                                                Long assignedToUserId, int reminderMinutes,
                                                int numberOfRemindersLeftToSend) {

        log.info("createLogBookEntryReplicate...parentGroup..." + groupId);

        // note: when we search on replicatedGroupId we want only the replicated entries, i.e., not
        LogBook parentLogBook = createLogBookEntry(createdByUserId, groupId, message, actionByDate, assignedToUserId,
                                                   null, reminderMinutes, numberOfRemindersLeftToSend, true);
        Timestamp commonCreatedDateTime = parentLogBook.getCreatedDateTime(); // so nanoseconds don't throw later queries

        // note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
        for (Group group : groupManagementService.findGroupAndSubGroupsById(groupId)) {
            log.info("createLogBookEntryReplicate...groupid..." + group.getId() + "...parentGroup..." + groupId);
            if (group.getId() != groupId) {
                LogBook lb = createReplicatedLogBookEntry(createdByUserId, commonCreatedDateTime, group.getId(), groupId, message,
                                                          actionByDate, assignedToUserId, reminderMinutes, numberOfRemindersLeftToSend);
            }
        }

        return parentLogBook;
    }

    // helper methods for signature simplification
    private LogBook createLogBookEntryReplicate(LogBook logBook) {
        return createLogBookEntryReplicate(logBook.getCreatedByUserId(), logBook.getGroupId(), logBook.getMessage(),
                                           logBook.getActionByDate(), logBook.getAssignedToUserId(), logBook.getReminderMinutes(),
                                           logBook.getNumberOfRemindersLeftToSend());
    }

    private LogBook createLogBookEntry(LogBook logBook) {
        return createLogBookEntry(logBook.getCreatedByUserId(), logBook.getGroupId(), logBook.getMessage(), logBook.getActionByDate(),
                                  logBook.getAssignedToUserId(), logBook.getReplicatedGroupId(), logBook.getReminderMinutes(),
                                  logBook.getNumberOfRemindersLeftToSend(), logBook.isRecorded());
    }

    /*
    If no reminders are to be sent then set the numberOfRemindersLeftToSend field to a negative value.
    If it is 0 it will be defaulted to 3
     */
    private LogBook createLogBookEntry(Long createdByUserId, Long groupId, String message, Timestamp actionByDate,
                                       Long assignedToUserId, Long replicatedGroupId, int reminderMinutes,
                                       int numberOfRemindersLeftToSend, boolean recorded) {
        LogBook logBook = new LogBook();
        if (assignedToUserId == null) {
            assignedToUserId = 0L; // else run into errors with event consumer
        }
        if (numberOfRemindersLeftToSend == 0) {
            numberOfRemindersLeftToSend = 3; // todo: replace with a logic based on group paid / not paid
        }
        if (reminderMinutes == 0) {
            reminderMinutes = defaultReminderMinutes;
        }

        logBook.setAssignedToUserId(assignedToUserId);
        logBook.setMessage(message);
        logBook.setActionByDate(actionByDate);
        logBook.setCreatedByUserId(createdByUserId);
        logBook.setGroupId(groupId);
        logBook.setNumberOfRemindersLeftToSend(numberOfRemindersLeftToSend);
        logBook.setReminderMinutes(reminderMinutes);
        logBook.setReplicatedGroupId(replicatedGroupId);
        logBook.setRecorded(recorded);

        LogBook savedLogbook = logBookRepository.save(logBook);
        jmsTemplateProducerService.sendWithNoReply("new-logbook",new LogBookDTO(savedLogbook));
        return  savedLogbook;
    }

    private LogBook createReplicatedLogBookEntry(Long createdByUserId, Timestamp commonCreatedDateTime, Long groupId,
                                                 Long replicatedGroupId, String message, Timestamp actionByDate, Long assignedToUserId,
                                                 int reminderMinutes, int numberOfRemindersLeftToSend) {
        LogBook logBook = new LogBook(createdByUserId, commonCreatedDateTime, groupId, replicatedGroupId, message, actionByDate);
        logBook.setAssignedToUserId(assignedToUserId);
        if (numberOfRemindersLeftToSend == 0) {
            numberOfRemindersLeftToSend = 3;
        }
        if (reminderMinutes == 0) {
            reminderMinutes = defaultReminderMinutes;
        }
        logBook.setNumberOfRemindersLeftToSend(numberOfRemindersLeftToSend);
        logBook.setReminderMinutes(reminderMinutes);
        logBook.setAssignedToUserId(0L); // cannot cascade this, can adjust later -- leaving null throws errors in consumers
        LogBook savedLogbook = logBookRepository.save(logBook);
        jmsTemplateProducerService.sendWithNoReply("new-logbook",new LogBookDTO(savedLogbook));
        return  savedLogbook;
    }

}
