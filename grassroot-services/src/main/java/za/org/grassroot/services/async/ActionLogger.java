package za.org.grassroot.services.async;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.GroupLog;

import java.util.Set;

public interface ActionLogger {
    void asyncLogActions(Set<ActionLog> actionLogs);

    void logActions(Set<ActionLog> actionLogs);
}
