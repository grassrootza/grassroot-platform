package za.org.grassroot.integration.services;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.MessengerSettings;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface MessengerSettingsService {

    @Transactional
    public void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive);


    @Transactional
    void updateUserGroupMessageSettings(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive, Instant reactivationTime) throws Exception;

    @Transactional(readOnly = true)
    boolean isCanSend(String userUid, String groupUid) throws Exception;

    @Transactional(readOnly = true)
    boolean isCanReceive(String userUid, String groupUid) throws Exception;

    @Transactional
    boolean messengerSettingExist(String userUid, String groupUid);

    @Transactional
    List<MessengerSettings> loadMutedUsersMessengerSettings();
}


