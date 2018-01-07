package za.org.grassroot.integration.socialmedia;

import org.springframework.util.MultiValueMap;

public interface SocialMediaBroker {

    IntegrationListResponse getCurrentIntegrations(String userUid);

    ManagedPagesResponse getManagedFacebookPages(String userUid);

    String initiateFacebookConnection(String userUid);

    ManagedPagesResponse completeFbConnect(String userUid, MultiValueMap<String, String> paramsToPass);

    GenericPostResponse postToFacebook(FBPostBuilder post);

    // returns null if no account, else returns its display name
    String isTwitterAccountConnected(String userUid);

    GenericPostResponse postToTwitter(TwitterPostBuilder post);

}
