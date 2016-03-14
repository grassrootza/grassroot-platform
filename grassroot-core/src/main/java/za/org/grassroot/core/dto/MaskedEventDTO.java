package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.MaskingUtil;

import java.sql.Timestamp;

/**
 * Created by luke on 2016/02/04.
 */
public class MaskedEventDTO {

    private String name;
    private String eventLocation;
    private Long id;
    private Timestamp createdDateTime;
    private Timestamp eventStartDateTime;

    private MaskedUserDTO createdByUser;
    private MaskedGroupDTO appliesToGroup;
    private boolean canceled;
    private EventType eventType;

    public MaskedEventDTO(Event event) {
        this.id = event.getId();
        this.name = MaskingUtil.maskName(event.getName());
//        this.eventLocation = MaskingUtil.maskName(event.getEventLocation());
        this.createdDateTime = event.getCreatedDateTime();
        this.eventStartDateTime = event.getCreatedDateTime();

        this.createdByUser = new MaskedUserDTO(event.getCreatedByUser());
        this.appliesToGroup = new MaskedGroupDTO(event.getAppliesToGroup());
        this.canceled = event.isCanceled();
        this.eventType = event.getEventType();
    }

    public String getName() {
        return name;
    }

    public String getEventLocation() {
        return eventLocation;
    }

    public Long getId() {
        return id;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public Timestamp getEventStartDateTime() {
        return eventStartDateTime;
    }

    public MaskedUserDTO getCreatedByUser() {
        return createdByUser;
    }

    public MaskedGroupDTO getAppliesToGroup() {
        return appliesToGroup;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public EventType getEventType() {
        return eventType;
    }
}
