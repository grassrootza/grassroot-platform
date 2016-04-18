package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/04/05.
 */
public interface GcmRegistrationRepository extends JpaRepository<GcmRegistration, Long> {

    GcmRegistration findByUser(User user);

    GcmRegistration findByUid(String uid);

    GcmRegistration findByRegistrationId(String registrationId);

}
