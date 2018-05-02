package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.enums.DataSubscriberType;

import java.time.Instant;

public class DataSubscriberAdminDTO extends DataSubscriberDTO{
    public Instant creationTime;
    public boolean active;
    public String primaryEmail;
    public DataSubscriberType subscriberType;

    public DataSubscriberAdminDTO(DataSubscriber dataSubscriber){
        super(dataSubscriber);
        this.creationTime = dataSubscriber.getCreationTime();
        this.active = dataSubscriber.isActive();
        this.primaryEmail = dataSubscriber.getPrimaryEmail();
        this.subscriberType = dataSubscriber.getSubscriberType();
    }
}
