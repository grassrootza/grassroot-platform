package za.org.grassroot.services.broadcasts;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Builder
public class EmailBroadcast {

    private String content;
    private String imageUid;

}
