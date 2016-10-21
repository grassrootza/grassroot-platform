package za.org.grassroot.integration.social;

/**
 * Created by luke on 2016/10/21.
 * major todo : stub this out ...
 */
public interface FacebookBroker {

    void tryConnectUserToFacebook(String userUid);

    void getUserFacebookFriends(String userUid, String friendList);

}
