package za.org.grassroot.integration.socialmedia;

import java.util.List;

public interface SocialMediaBroker {

    List<FacebookAccount> getFacebookPages(String userUid);

    String initiateFacebookConnection(String userUid);

    String initiateTwitterConnection(String userUid);

    List<FacebookAccount> completeFbConnection(String userUid, String code);

    TwitterAccount completeTwitterConnection(String userUid, String oauthToken, String oauthVerifier);

    List<GenericPostResponse> postToFacebook(List<FBPostBuilder> posts);

    // returns null if no account, else returns its display name
    TwitterAccount isTwitterAccountConnected(String userUid);

    GenericPostResponse postToTwitter(TwitterPostBuilder post);

    boolean removeIntegration(String userUid, String providerId);

}
