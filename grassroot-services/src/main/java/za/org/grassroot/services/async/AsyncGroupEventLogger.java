package za.org.grassroot.services.async;

import za.org.grassroot.core.domain.GroupLog;

import java.util.Set;

public interface AsyncGroupEventLogger {
    void logGroupEvents(Set<GroupLog> groupLogs);
}
