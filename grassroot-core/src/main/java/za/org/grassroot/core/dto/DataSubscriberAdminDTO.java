package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.enums.DataSubscriberType;

import java.util.List;

@Getter
public class DataSubscriberAdminDTO extends DataSubscriberDTO{
    protected long creationTime;
    protected boolean active;
    protected String primaryEmail;
    protected DataSubscriberType subscriberType;
    protected List<String> pushEmails;
    protected List<String> usersWithAccess;


    public DataSubscriberAdminDTO(DataSubscriber dataSubscriber){
        super(dataSubscriber);
        this.creationTime = dataSubscriber.getCreationTime().toEpochMilli();
        this.active = dataSubscriber.isActive();
        this.primaryEmail = dataSubscriber.getPrimaryEmail();
        this.subscriberType = dataSubscriber.getSubscriberType();
        this.pushEmails = dataSubscriber.getPushEmails();
        this.usersWithAccess = dataSubscriber.getUsersWithAccess();
    }
}
