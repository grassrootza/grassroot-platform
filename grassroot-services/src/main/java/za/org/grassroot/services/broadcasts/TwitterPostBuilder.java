package za.org.grassroot.services.broadcasts;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Builder
public class TwitterPostBuilder {

    private String postingUserUid;
    private String message;
    private String imageKey;

}
