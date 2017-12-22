package za.org.grassroot.services.broadcasts;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Builder
public class FBPostBuilder {

    private String postingUserUid;
    private String facebookPageId;
    private String message;
    private String link;
    private String imageKey;
    private String imageCaption;

}
