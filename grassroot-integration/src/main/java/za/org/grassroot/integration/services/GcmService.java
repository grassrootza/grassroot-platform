package za.org.grassroot.integration.services;

import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/04/05.
 */

public interface GcmService {

    GcmRegistration load(String uid);
    GcmRegistration loadByRegistrationId(String registrationId);
    String getGcmKey(User user);
    GcmRegistration loadByUser(User user);
    GcmRegistration registerUser(User user,String registrationId);

}
