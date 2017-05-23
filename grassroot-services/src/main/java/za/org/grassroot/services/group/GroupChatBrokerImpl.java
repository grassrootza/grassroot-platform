package za.org.grassroot.services.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupChatSettingsRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/09/08.
 */
@Service
public class GroupChatBrokerImpl implements GroupChatBroker {

    private static final Logger logger = LoggerFactory.getLogger(GroupChatBrokerImpl.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupChatSettingsRepository groupChatSettingsRepository;

    @Autowired
    public GroupChatBrokerImpl(UserRepository userRepository, GroupRepository groupRepository,
                               GroupChatSettingsRepository groupChatSettingsRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupChatSettingsRepository = groupChatSettingsRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "groupChatSettings", key = "userUid + '_'+ groupUid")
    public GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = groupChatSettingsRepository.findByUserAndGroup(user, group);

        if (groupChatSettings == null) {
            throw new GroupChatSettingNotFoundException("Group chat setting not found found for user with uid " + userUid);
        }

        return groupChatSettings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> usersMutedInGroup(String groupUid) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        List<GroupChatSettings> groupChatSettingses = groupChatSettingsRepository.findByGroupAndActiveAndCanSend(group, true, false);
        List<String> mutedUsersUids = new ArrayList<>();
        for (GroupChatSettings groupChatSettings : groupChatSettingses) {
            User user = groupChatSettings.getUser();
            mutedUsersUids.add(user.getUsername());
        }
        return mutedUsersUids;
    }

}
