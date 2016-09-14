package za.org.grassroot.integration.services;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.MessengerSettings;
import za.org.grassroot.integration.exception.MessengerSettingNotFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface MessengerSettingsService {

    @Transactional
    public void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive);

    @Transactional
    public MessengerSettings load(String userUid, String groupUid);


    @Transactional
    void updateUserGroupMessageSettings(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive, Instant reactivationTime) throws MessengerSettingNotFoundException;

    @Transactional(readOnly = true)
    boolean isCanSend(String userUid, String groupUid) throws MessengerSettingNotFoundException;

    @Transactional
    void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws MessengerSettingNotFoundException;

    @Transactional(readOnly = true)
    boolean isCanReceive(String userUid, String groupUid) throws Exception;

    @Transactional(readOnly =true)
    boolean messengerSettingExist(String userUid, String groupUid);

    @Transactional
    List<MessengerSettings> loadMutedUsersMessengerSettings();
}


