package za.org.grassroot.integration.socialmedia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class ManagedPage {

    private String providerUserId;
    private String displayName;
    private String imageUrl;

}
