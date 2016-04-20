package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GcmRegistrationRepository;

import java.time.Instant;

/**
 * Created by paballo on 2016/04/05.
 */
@Service
public class GcmManager implements GcmService {
    @Autowired
    GcmRegistrationRepository gcmRegistrationRepository;

    @Override
    public GcmRegistration load(String uid) {
        return null;
    }

    @Override
    public GcmRegistration loadByRegistrationId(String registrationId) {
        return gcmRegistrationRepository.findByRegistrationId(registrationId);
    }

    @Override
    public String getGcmKey(User user) {
        return gcmRegistrationRepository.findByUser(user).getRegistrationId();
    }

    @Override
    public GcmRegistration registerUser(User user, String registrationId) {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(user);
        if (gcmRegistration != null) {
            gcmRegistration.setRegistrationId(registrationId);
        } else {
            gcmRegistration = new GcmRegistration(user, registrationId, Instant.now());
        }

        return gcmRegistrationRepository.save(gcmRegistration);
    }


}
