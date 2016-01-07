package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 12/5/15.
 */
@Component
public class LogBookManager implements LogBookService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    LogBookRepository logBookRepository;

    @Autowired
    GroupManagementService groupManagementService;


    @Override
    public LogBook load(Long logBookId) {
        return logBookRepository.findOne(logBookId);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Long groupId) {
        return logBookRepository.findAllByGroupId(groupId);
    }

    @Override
    public List<LogBook> getAllLogBookEntriesForGroup(Long groupId, boolean completed) {
        // use an old timestamp both so we prune the really old entries, and to get around half-formed ("null due date") entries
        log.info("Getting all logBook entries for groupId ... " + groupId + " ... and completed state: " + completed);
        return logBookRepository.findAllByGroupIdAndCompletedAndActionByDateGreaterThan(
                groupId, completed, Timestamp.valueOf(LocalDateTime.now().minusYears(1L)));
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
    public LogBook create(Long createdByUserId, Long groupId, String message) {
        return createLogBookEntry(createdByUserId, groupId, message, null, 0L, 0L, 0, 0);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message, Timestamp actionByDate) {
        return createLogBookEntry(createdByUserId, groupId, message, actionByDate, 0L, 0L, 0, 0);
    }

    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message, Timestamp actionByDate, Long assignToUserId) {
        return createLogBookEntry(createdByUserId, groupId, message, actionByDate, assignToUserId, 0L, 0, 0);
    }
    @Override
    public LogBook create(Long createdByUserId, Long groupId, String message, boolean replicateToSubGroups) {
        if (replicateToSubGroups) {
            return createLogBookEntryReplicate(createdByUserId, groupId, message, null, 0L, 0, 0);

        } else {
            return createLogBookEntry(createdByUserId, groupId, message, null, 0L, 0L, 0, 0);

        }
    }

    // method called from front end to save a (mostly) fully formed entry
    @Override
    public LogBook create(LogBook logBookToSave, boolean replicateToSubGroups) {
        // todo: proper checks and validation
        if (!replicateToSubGroups)
            return logBookRepository.save(logBookToSave);
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
    public LogBook setCompleted(Long logBookId, Long completedByUserId) {
        return setCompletedWithDate(logBookId, completedByUserId, Timestamp.valueOf(LocalDateTime.now()));
    }

    @Override
    public LogBook setCompletedWithDate(Long logBookId, Long completedByUserId, Timestamp completedDate) {
        // todo: checks to make sure not already completed, possibly also do a check for permissions here
        LogBook logBook = logBookRepository.findOne(logBookId);
        logBook.setCompleted(true);
        logBook.setCompletedByUserId(completedByUserId);
        logBook.setCompletedDate(completedDate);
        return logBookRepository.save(logBook);
    }

    @Override
    public LogBook setCompleted(Long logBookId, Timestamp completedDate) {
        LogBook logBook = logBookRepository.findOne(logBookId);
        logBook.setCompleted(true);
        logBook.setCompletedByUserId(logBook.getAssignedToUserId()); // if no user assigned, then both are null, as we want
        logBook.setCompletedDate(completedDate);
        return logBookRepository.save(logBook);
    }

    @Override
    public LogBook setCompleted(Long logBookId) {
        return setCompleted(logBookId, Timestamp.valueOf(LocalDateTime.now()));
    }

    private LogBook createLogBookEntryReplicate(Long createdByUserId, Long groupId, String message, Timestamp actionByDate,
                                                Long assignedToUserId, int reminderMinutes,
                                                int numberOfRemindersLeftToSend) {

        log.info("createLogBookEntryReplicate...parentGroup..." + groupId);

        LogBook parentLogBook = new LogBook();
        for (Group group : groupManagementService.findGroupAndSubGroupsById(groupId)) {
            log.info("createLogBookEntryReplicate...groupid..." + group.getId() + "...parentGroup..." + groupId);
            LogBook lb = createLogBookEntry(createdByUserId, group.getId(), message, actionByDate, assignedToUserId,
                                            groupId, reminderMinutes, numberOfRemindersLeftToSend);
            if (group.getId() == groupId) {
                parentLogBook = lb;
            }
        }

        return parentLogBook;
    }

    private LogBook createLogBookEntryReplicate(LogBook logBook) {
        return createLogBookEntryReplicate(logBook.getCreatedByUserId(), logBook.getGroupId(), logBook.getMessage(),
                                           logBook.getActionByDate(), logBook.getAssignedToUserId(), logBook.getReminderMinutes(),
                                           logBook.getNumberOfRemindersLeftToSend());
    }

    /*
    If no reminders are to be sent then set the numberOfRemindersLeftToSend field to a negative value.
    If it is 0 it will be defaulted to 3
     */
    private LogBook createLogBookEntry(Long createdByUserId, Long groupId, String message, Timestamp actionByDate,
                                       Long assignedToUserId, Long replicatedGroupId, int reminderMinutes,
                                       int numberOfRemindersLeftToSend) {
        LogBook logBook = new LogBook();
        if (numberOfRemindersLeftToSend == 0) {
            numberOfRemindersLeftToSend = 3;
        }
        /*
        Minus value will send a reminder before actionByDate, Plus value will send a reminder x minutes after
        actionByDate
         */
        if (reminderMinutes == 0) {
            reminderMinutes = -1440; // think it is 24 hours
        }

        logBook.setAssignedToUserId(assignedToUserId);
        logBook.setMessage(message);
        logBook.setActionByDate(actionByDate);
        logBook.setCreatedByUserId(createdByUserId);
        logBook.setGroupId(groupId);
        logBook.setNumberOfRemindersLeftToSend(numberOfRemindersLeftToSend);
        logBook.setReminderMinutes(reminderMinutes);
        logBook.setReplicatedGroupId(replicatedGroupId);

        return logBookRepository.save(logBook);
    }
}
