package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.ActionLogType;

/**
 * Created by luke on 2017/03/10.
 * note : check if we can use this to clean up ImageRecord
 */
public interface TaskLog<T extends Task> extends ActionLog {

    User getUser();

    @Override
    default ActionLogType getActionLogType() {
        return ActionLogType.TASK_LOG;
    }

    T getTask();

    GeoLocation getLocation();

    boolean isCreationLog();

    String getTag();

}
