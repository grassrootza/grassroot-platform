package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GcmRegistrationRepository;

/**
 * Created by paballo on 2016/04/05.
 */
@Service
public class GcmManager implements GcmService {

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Override
    @Transactional(readOnly = true)
    public GcmRegistration load(String uid) {
        return gcmRegistrationRepository.findByUid(uid);
    }

    @Override
    @Transactional(readOnly = true)
    public GcmRegistration loadByRegistrationId(String registrationId) {
        return gcmRegistrationRepository.findByRegistrationId(registrationId);
    }

    @Override
    @Transactional(readOnly = true)
    public String getGcmKey(User user) {
        return gcmRegistrationRepository.findByUser(user).getRegistrationId();
    }

    @Override
    @Transactional
    public GcmRegistration registerUser(User user, String registrationId) {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(user);
        if (gcmRegistration != null) {
            gcmRegistration.setRegistrationId(registrationId);
        } else {
            gcmRegistration = new GcmRegistration(user, registrationId);
        }

        return gcmRegistrationRepository.save(gcmRegistration);
    }

    @Override
    @Transactional
    public void unregisterUser(User user) {
        GcmRegistration gcmRegistration = gcmRegistrationRepository.findByUser(user);
        gcmRegistrationRepository.delete(gcmRegistration);
    }


}
