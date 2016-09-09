package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.MessengerSettings;
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
public class MessengerSettingsManager implements MessengerSettingsService {

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    MessengerSettingsRepository messengerSettingsRepository;

    @Override
    @Transactional
    public void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive) {
        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        MessengerSettings messengerSettings = new MessengerSettings(user,group,active,true,true,true);
        messengerSettingsRepository.save(messengerSettings);
    }

    @Override
    @Transactional
    public void updateUserGroupMessageSettings(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive, Instant reactivationTime) throws MessengerSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);
        Objects.nonNull(reactivationTime);


        User user =  userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        MessengerSettings messengerSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        if(null== messengerSettings){
            throw new MessengerSettingNotFoundException("Message settings not found for user with id " + userUid);
        }
        messengerSettings.setActive(active);
        messengerSettings.setCanSend(canSend);
        messengerSettings.setCanReceive(canReceive);
        messengerSettings.setReactivationTime(reactivationTime);

        messengerSettingsRepository.save(messengerSettings);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCanSend(String userUid, String groupUid) throws MessengerSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        MessengerSettings messengerSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        if(null== messengerSettings){
            throw new MessengerSettingNotFoundException("Message settings not found for user with id " + userUid);
        }

        return messengerSettings.isCanSend();
    }

    @Override
    @Transactional
    public void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws MessengerSettingNotFoundException {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        MessengerSettings messengerSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        if(null== messengerSettings){
            throw new MessengerSettingNotFoundException("Message settings not found for user with id " + userUid);
        }
        messengerSettings.setActive(active);
        messengerSettings.setUserInitiated(userInitiated);
        messengerSettings.setCanSend(active);

        if(userInitiated ){
            messengerSettings.setCanReceive(active);
        }

        messengerSettingsRepository.save(messengerSettings);


    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCanReceive(String userUid, String groupUid) throws Exception {
        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        MessengerSettings messengerSettings = messengerSettingsRepository.findByUserAndGroup(user,group);

        if(null== messengerSettings){
            throw new Exception("Message settings not found for user with id " + userUid);
        }

        return messengerSettings.isCanSend();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean messengerSettingExist(String userUid, String groupUid){

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        MessengerSettings messengerSettings = messengerSettingsRepository.findByUserAndGroup(user,group);
        return (messengerSettings != null);

    }


    @Override
    @Transactional(readOnly = true)
    public List<MessengerSettings> loadMutedUsersMessengerSettings(){
        return  messengerSettingsRepository.findByActiveAndUserInitiatedAndReactivationTimeBefore(false,false, Instant.now());

    }


}
