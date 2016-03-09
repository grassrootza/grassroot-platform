package za.org.grassroot.services;

import za.org.grassroot.core.domain.GroupLog;

import java.util.Set;

public interface AsyncGroupEventLogger {
    void logGroupEvents(Set<GroupLog> groupLogs);
}
