package za.org.grassroot.core.domain;

import java.time.Instant;

public interface EntityForUserResponse<P extends UidIdentifiable> extends UidIdentifiable {

    void setParent(P parent);
    P getParent();

    User getCreatedByUser();

    Instant getDeadlineTime();

}
