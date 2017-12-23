package za.org.grassroot.integration.socialmedia;

import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Builder @ApiModel
public class TwitterPostBuilder {

    private String postingUserUid;
    private String message;
    private String imageKey;

}
