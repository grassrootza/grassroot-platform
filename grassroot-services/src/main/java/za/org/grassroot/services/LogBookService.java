package za.org.grassroot.services;

import za.org.grassroot.core.domain.LogBook;

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

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId, boolean completed);

    LogBook create(Long createdByUserId, Long groupId, String message);

    LogBook create(Long createdByUserId, Long groupId, String message, Date actionByDate);

    LogBook create(Long createdByUserId, Long groupId, String message, Date actionByDate, Long assignToUserId);

    LogBook create(Long createdByUserId, Long groupId, String message, boolean replicateToSubGroups);

    LogBook setDueDate(Long logBookId, LocalDateTime actionByDateTime);

    LogBook setAssignedToUser(Long logBookId, Long assignedToUserId);

    LogBook setMessage(Long logBookId, String message);
}
