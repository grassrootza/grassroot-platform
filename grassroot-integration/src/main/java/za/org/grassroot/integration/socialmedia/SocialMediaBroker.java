package za.org.grassroot.integration.socialmedia;

import java.util.Map;

public interface SocialMediaBroker {

    boolean isFacebookPageConnected(String userUid);

    ManagedPagesResponse getManagedFacebookPages(String userUid);

    GenericPostResponse postToFacebook(FBPostBuilder post);

    boolean isTwitterAccountConnected(String userUid);

    GenericPostResponse postToTwitter(TwitterPostBuilder post);

}
