package za.org.grassroot.integration.xmpp;

import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

import java.io.IOException;

/**
 * Created by paballo on 2016/04/05.
 */
public interface GcmService {

    GcmRegistration load(String uid);

    GcmRegistration registerUser(User user, String registrationId);

    void unregisterUser(User user);

    String getGcmKey(User user);

    boolean hasGcmKey(User user);

    void subscribeToTopic(String registrationId, String topicId) throws IOException;

    void refreshAllGroupTopicSubscriptions(String userUid, String registrationId);

    void unsubscribeFromTopic(String registrationId, String topicId) throws Exception;

}
