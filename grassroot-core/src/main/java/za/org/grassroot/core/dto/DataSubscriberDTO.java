package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.livewire.DataSubscriber;

@Getter
public class DataSubscriberDTO {
    protected String uid;
    protected String displayName;

    public DataSubscriberDTO(DataSubscriber dataSubscriber){
        this.uid = dataSubscriber.getUid();
        this.displayName = dataSubscriber.getDisplayName();
    }
}
