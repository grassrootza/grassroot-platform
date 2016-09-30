package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface MessengerSettingsRepository extends JpaRepository<GroupChatSettings, Long> {

    GroupChatSettings findByUserAndGroup(User user, Group group);

    List<GroupChatSettings> findByUser(User user);

    List<GroupChatSettings> findByActiveAndUserInitiatedAndReactivationTimeBefore(boolean active, boolean userInitiated, Instant reactivationTime);

 }