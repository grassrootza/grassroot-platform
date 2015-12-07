package za.org.grassroot.services;

import za.org.grassroot.core.domain.LogBook;

import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookService {
    List<LogBook> getAllLogBookEntriesForGroup(Long groupId);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId);

    List<LogBook> getAllReplicatedEntriesForGroup(Long groupId, boolean completed);

    LogBook create(Long createdByUserId, Long groupId, String message);

    LogBook create(Long createdByUserId, Long groupId, String message, boolean replicateToSubGroups);
}
