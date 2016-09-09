package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.MessengerSettings;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface MessengerSettingsRepository extends JpaRepository<MessengerSettings, Long> {

    MessengerSettings findByUserAndGroup(User user, Group group);

    List<MessengerSettings> findByUser(User user);

    List<MessengerSettings> findByActiveAndUserInitiatedAndReactivationTimeBefore(boolean active, boolean userInitiated, Instant reactivationTime);

 }