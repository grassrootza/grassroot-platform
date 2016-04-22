package za.org.grassroot.services.async;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.GroupLog;

import java.util.Set;

public interface AsyncActionLogger {
    void logGroupEvents(Set<ActionLog> actionLogs);
}
