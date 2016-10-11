package za.org.grassroot.integration.services;

import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;

import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface GroupChatSettingsService {

    void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive);

    GroupChatSettings load(String userUid, String groupUid)  throws GroupChatSettingNotFoundException;

    boolean isCanSend(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws Exception;

    boolean messengerSettingExist(String userUid, String groupUid);

    List<GroupChatSettings> loadUsersToBeUnmuted();

    List<String> usersMutedInGroup(String groupUid);
}


