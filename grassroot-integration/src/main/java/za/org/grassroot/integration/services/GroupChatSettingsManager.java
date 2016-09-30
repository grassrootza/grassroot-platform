package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MessengerSettingsRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.exception.MessengerSettingNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/09/08.
 */
@Service
public class GroupChatSettingsManager implements GroupChatSettingsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MessengerSettingsRepository messengerSettingsRepository;



    @Override
    @Transactional
    public void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive) {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = new GroupChatSettings(user,group,active,true,true,true);
        messengerSettingsRepository.save(groupChatSettings);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "groupChatSettings")
    public GroupChatSettings load(String userUid, String groupUid) {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = messengerSettingsRepository.findByUserAndGroup(user, group);

        return groupChatSettings;
    }

    @Override
    @Transactional
    public void updateUserGroupMessageSettings(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive, Instant reactivationTime) throws MessengerSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);
        Objects.nonNull(reactivationTime);


        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        if(null== groupChatSettings){
            throw new MessengerSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }
        groupChatSettings.setActive(active);
        groupChatSettings.setCanSend(canSend);
        groupChatSettings.setCanReceive(canReceive);
        groupChatSettings.setReactivationTime(reactivationTime);

        messengerSettingsRepository.save(groupChatSettings);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCanSend(String userUid, String groupUid) throws MessengerSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        if(null== groupChatSettings){
            throw new MessengerSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }

        return groupChatSettings.isCanSend();
    }

    @Override
    @Transactional
    public void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws Exception {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        if(null== groupChatSettings){
            throw new MessengerSettingNotFoundException("Message settings not found for user with uid " + userUid);
        }
        groupChatSettings.setActive(active);
        groupChatSettings.setUserInitiated(userInitiated);
        groupChatSettings.setCanSend(active);
        if(userInitiated){
            groupChatSettings.setCanReceive(active);
        }
        messengerSettingsRepository.save(groupChatSettings);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCanReceive(String userUid, String groupUid) throws Exception {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = messengerSettingsRepository.findByUserAndGroup(user,group);

        if(null== groupChatSettings){
            throw new Exception("Message settings not found for user with uid " + userUid);
        }

        return groupChatSettings.isCanSend();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean messengerSettingExist(String userUid, String groupUid){

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        GroupChatSettings groupChatSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        return (groupChatSettings != null);

    }


    @Override
    @Transactional(readOnly = true)
    public List<GroupChatSettings> loadUsersToBeUnmuted(){
        return  messengerSettingsRepository.findByActiveAndUserInitiatedAndReactivationTimeBefore(false,false, Instant.now());

    }


}
