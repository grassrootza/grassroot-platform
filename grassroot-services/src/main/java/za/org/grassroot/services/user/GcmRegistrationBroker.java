package za.org.grassroot.services.user;

import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

import java.io.IOException;

/**
 * Created by paballo on 2016/04/05.
 */
public interface GcmRegistrationBroker {

    GcmRegistration registerUser(User user, String registrationId);

    void unregisterUser(User user);

    void changeTopicSubscription(String userUid, String topicId, boolean subscribe) throws IOException;

    void refreshAllGroupTopicSubscriptions(String userUid, String registrationId);

    boolean hasGcmKey(User user);

}
