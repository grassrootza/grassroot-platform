package za.org.grassroot.integration.services;

import org.springframework.scheduling.annotation.Async;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.io.IOException;
import java.util.List;

/**
 * Created by paballo on 2016/04/05.
 */
public interface GcmService {

    GcmRegistration load(String uid);

    String getGcmKey(User user);

    boolean hasGcmKey(User user);

    GcmRegistration registerUser(User user, String registrationId);

    void  unregisterUser(User user);

    void subscribeToTopic(String registrationId, String topicId) throws IOException;

    void refreshAllGroupTopicSubscriptions(String userUid, String registrationId);

    @Async
    void unsubscribeFromTopic(String registrationId, String topicId) throws Exception;

    @Async
    void pingUserForGroupChat(User user, Group group);

}
