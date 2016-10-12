package za.org.grassroot.integration;

import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.integration.domain.IncomingChatMessage;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;

import java.util.List;

/**
 * Created by paballo on 2016/09/08.
 */
public interface GroupChatService {

    void processAndRouteIncomingChatMessage(IncomingChatMessage message);

    GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive);

    boolean isCanSend(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws Exception;

    boolean messengerSettingExist(String userUid, String groupUid);

    List<GroupChatSettings> loadUsersToBeUnmuted();

    List<String> usersMutedInGroup(String groupUid);
}


