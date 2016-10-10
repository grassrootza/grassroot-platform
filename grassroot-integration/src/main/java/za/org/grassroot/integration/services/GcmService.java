package za.org.grassroot.integration.services;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
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

    GcmRegistration loadByRegistrationId(String registrationId);

    String getGcmKey(User user);

    @Transactional
    boolean hasGcmKey(User user);

    GcmRegistration registerUser(User user, String registrationId);

    void  unregisterUser(User user);

    @Transactional
    void subscribeToTopic(String registrationId, String topicId) throws IOException;

    @Transactional
    @Async
    void unsubscribeFromTopic(String registrationId, String topicId) throws Exception;

    @Transactional
    @Async
    void batchAddUsersToTopic(List<String> registrationIds, String topicId) throws IOException;

    @Transactional(readOnly = true)
    @Async
    void pingUserForGroupChat(User user, Group group);

}
