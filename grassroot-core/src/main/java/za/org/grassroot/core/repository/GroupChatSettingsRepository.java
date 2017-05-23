package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface GroupChatSettingsRepository extends JpaRepository<GroupChatSettings, Long> {

    GroupChatSettings findByUserAndGroup(User user, Group group);

    List<GroupChatSettings> findByGroupAndActiveAndCanSend(Group group, boolean active, boolean canSend);

 }