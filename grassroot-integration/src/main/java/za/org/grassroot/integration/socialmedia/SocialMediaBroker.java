package za.org.grassroot.integration.socialmedia;

import org.springframework.util.MultiValueMap;

public interface SocialMediaBroker {

    IntegrationListResponse getCurrentIntegrations(String userUid);

    ManagedPagesResponse getManagedFacebookPages(String userUid);

    String initiateFacebookConnection(String userUid);

    String initiateTwitterConnection(String userUid);

    ManagedPagesResponse completeIntegrationConnect(String userUid, String providerId, MultiValueMap<String, String> paramsToPass);

    GenericPostResponse postToFacebook(FBPostBuilder post);

    // returns null if no account, else returns its display name
    ManagedPage isTwitterAccountConnected(String userUid);

    GenericPostResponse postToTwitter(TwitterPostBuilder post);

    IntegrationListResponse removeIntegration(String userUid, String providerId);

}
