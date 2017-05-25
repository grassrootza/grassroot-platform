package za.org.grassroot.services.group;

import za.org.grassroot.core.domain.GroupChatSettings;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;

import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/09/08.
 */
public interface GroupChatBroker {

    GroupChatSettings load(String userUid, String groupUid) throws GroupChatSettingNotFoundException;

    List<String> usersMutedInGroup(String groupUid);

}


