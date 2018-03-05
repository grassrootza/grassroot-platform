package za.org.grassroot.integration.socialmedia;

import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import za.org.grassroot.core.domain.media.MediaFunction;

@Getter @Setter @Builder @ToString @ApiModel
public class FBPostBuilder {

    private String postingUserUid;
    private String facebookPageId;
    private String message;
    private String linkUrl;
    private String linkName;
    private String imageKey;
    private MediaFunction imageMediaType;
    private String imageCaption;

}
