package za.org.grassroot.integration.socialmedia;

import org.springframework.util.MultiValueMap;

import java.util.List;

public interface SocialMediaBroker {

    List<FacebookAccount> getFacebookPages(String userUid);

    String initiateFacebookConnection(String userUid);

    List<FacebookAccount> completeFbConnection(String userUid, String code);

    List<GenericPostResponse> postToFacebook(List<FBPostBuilder> posts);

    IntegrationListResponse getCurrentIntegrations(String userUid);

    ManagedPagesResponse getManagedPages(String userUid, String providerId);

    String initiateTwitterConnection(String userUid);

    ManagedPagesResponse completeIntegrationConnect(String userUid, String providerId, MultiValueMap<String, String> paramsToPass);

    // returns null if no account, else returns its display name
    ManagedPage isTwitterAccountConnected(String userUid);

    GenericPostResponse postToTwitter(TwitterPostBuilder post);

    IntegrationListResponse removeIntegration(String userUid, String providerId);

}
