package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.ActionLogType;

import java.time.Instant;

public interface ActionLog {

    String getUid();

    User getUser();

    Instant getCreationTime();

    ActionLogType getActionLogType();

}
