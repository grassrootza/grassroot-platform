package za.org.grassroot.integration.utils;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GroupChatMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/09/20.
 */
public class MessageUtils {

    public static Map<String, Object> prePopWithGroupData(Group group) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.GROUP_UID, group.getUid());
        data.put(Constants.GROUP_NAME, group.getName());
        data.put("groupIcon", group.getImageUrl());
        return data;
    }

    public static Map<String, Object> generatePingMessageData(User user, Group group) {

        Map<String, Object> data = new HashMap<>();
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        data.put(Constants.GROUP_UID, group.getUid());
        data.put(Constants.GROUP_NAME, group.getGroupName());
        data.put("groupIcon", group.getImageUrl());
        data.put("messageId", messageId);
        data.put("messageUid", UIDGenerator.generateId());
        data.put(Constants.TITLE, user.nameToDisplay());
        data.put("type", "ping");
        data.put("userUid", user.getUid());
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", Instant.now());

        return data;
    }

    public static boolean isCommand(GroupChatMessage input) {
        String text = (String)input.getData().get("message");
        return text.startsWith("/");
    }

    public static String[] tokenize(String message) {
        if (message.contains("/meeting")) {
            message = message.replace("/meeting", "");
        }
        if (message.contains("/vote")) {
            message = message.replace("/vote", "");
        }
        if (message.contains("/todo")) {
            message = message.replace("/todo", "");
        }
        return message.split(",");
    }


    public static Map<String, Object> generateUserMutedResponseData(MessageSourceAccessor messageSourceAccessor, GroupChatMessage input, Group group) {
        String groupUid = (String) input.getData().get(Constants.GROUP_UID);
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.chat.muted");
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.GROUP_UID, groupUid);
        data.put(Constants.GROUP_NAME, group.getGroupName());
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageUid());
        data.put(Constants.TITLE, "Grassroot");
        data.put(Constants.BODY, responseMessage);
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;
    }


}


