package za.org.grassroot.integration.socialmedia;

public interface SocialMediaBroker {

    boolean isFacebookPageConnected(String userUid);

    ManagedPagesResponse getManagedFacebookPages(String userUid);

    GenericPostResponse postToFacebook(FBPostBuilder post);

    // returns null if no account, else returns its display name
    String isTwitterAccountConnected(String userUid);

    GenericPostResponse postToTwitter(TwitterPostBuilder post);

}
