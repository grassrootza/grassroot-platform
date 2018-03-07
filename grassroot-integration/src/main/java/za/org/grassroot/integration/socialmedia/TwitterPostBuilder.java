package za.org.grassroot.integration.socialmedia;

import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.media.MediaFunction;

@Getter @Setter @Builder @ApiModel
public class TwitterPostBuilder {

    private String postingUserUid;
    private String message;
    private MediaFunction imageMediaFunction;
    private String imageKey;

}
