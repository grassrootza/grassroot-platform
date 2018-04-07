package za.org.grassroot.core.dto;

import lombok.Getter;

@Getter
public class DataSubscriberDTO {
    protected String uid;
    protected String displayName;

    public DataSubscriberDTO(String uid,String displayName){
        this.uid = uid;
        this.displayName = displayName;
    }
}
