package za.org.grassroot.integration;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.domain.MQTTPayload;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;

import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/09/08.
 */
public interface GroupChatService {

    void processCommandMessage(MQTTPayload incoming);

    void markMessagesAsRead(String groupUid, String groupName, Set<String> messageUids);

    GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    void createUserGroupMessagingSetting(String userUid, String groupUid, boolean active, boolean canSend, boolean canReceive);

    boolean isCanSend(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws Exception;

    void createGroupChatMessageStats(MQTTPayload payload);

    boolean messengerSettingExist(String userUid, String groupUid);

    List<GroupChatSettings> loadUsersToBeUnmuted();

    List<String> usersMutedInGroup(String groupUid);

    void pingToSync(User addingUser, User addedUser, Group group);

    void addAllGroupMembersToChat(Group group, User initiatingUser);

}


