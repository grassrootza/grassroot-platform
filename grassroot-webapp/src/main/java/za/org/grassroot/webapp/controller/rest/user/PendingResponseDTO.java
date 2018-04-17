package za.org.grassroot.webapp.controller.rest.user;

import lombok.Getter;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.JpaEntityType;

@Getter
public class PendingResponseDTO {

    public boolean hasPendingResponse;

    public JpaEntityType entityType;
    public String entityUid;

    public String title;
    public String description;

    public String parentName;
    public String creatorName;

    public long dueByTimestampMillis;

    public PendingResponseDTO() {
        this.hasPendingResponse = false;
    }

    public PendingResponseDTO(EntityForUserResponse entity) {
        this.hasPendingResponse = true;
        this.entityUid = entity.getUid();
        this.entityType = entity.getJpaEntityType();
        this.title = entity.getName();
        this.description = entity.getDescription();
        this.parentName = entity.getParent().getName();
        this.creatorName = entity.getCreatedByUser().getName();
        this.dueByTimestampMillis = entity.getDeadlineTime().toEpochMilli();
    }

}
