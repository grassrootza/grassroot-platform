package za.org.grassroot.integration;

import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;

import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/09/08.
 */
public interface GroupChatService {

    void markMessagesAsRead(String groupUid, String groupName, Set<String> messageUids);

    GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws Exception;

    List<String> usersMutedInGroup(String groupUid);

}


