package za.org.grassroot.core.domain;

import java.time.Instant;

public interface ActionLog {

    String getUid();

    User getUser();

    Instant getCreationTime();

}
