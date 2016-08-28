package za.org.grassroot.integration.services;

import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/04/05.
 */

public interface GcmService {

    GcmRegistration load(String uid);

    GcmRegistration loadByRegistrationId(String registrationId);

    String getGcmKey(User user);

    GcmRegistration registerUser(User user,String registrationId);

    void  unregisterUser(User user);

}
