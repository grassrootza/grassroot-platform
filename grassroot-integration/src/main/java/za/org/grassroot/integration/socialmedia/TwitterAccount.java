package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString @NoArgsConstructor @Getter @Setter
public class TwitterAccount {

    private String displayName;
    private String twitterUserId;
    private String profileImageUrl;

}
