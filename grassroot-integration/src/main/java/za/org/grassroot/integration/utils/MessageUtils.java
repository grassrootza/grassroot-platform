package za.org.grassroot.integration.utils;

import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;

import java.time.Instant;
import java.util.*;

/**
 * Created by paballo on 2016/09/20.
 */
public class MessageUtils {

    public static Map<String, Object> generateCommandResponseData(MessageSourceAccessor messageSourceAccessor, GcmUpstreamMessage input, Group group, String[] tokens) {
        String groupUid = (String) input.getData().get(Constants.GROUP_UID);
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        String message = String.valueOf(input.getData().get("message"));
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.GROUP_UID, groupUid);
        data.put(Constants.GROUP_NAME, group.getGroupName());
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageId());
        data.put(Constants.TITLE, "Grassroot");
        if (message.contains("/meeting")) {
            String text = messageSourceAccessor.getMessage("gcm.xmpp.command.meeting",tokens);
            data.put("type", "meeting");
            data.put(Constants.BODY, text);
        }
        data.put("tokens", Arrays.asList(tokens));
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;

    }


    public static Map<String, Object> generateChatMessageData(GcmUpstreamMessage input, User user, Group group) {

        String message = (String) input.getData().get("message");
        Map<String, Object> data = new HashMap<>();
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        data.put(Constants.GROUP_UID, group.getUid());
        data.put(Constants.GROUP_NAME, group.getGroupName());
        data.put("groupIcon", group.getImageUrl());
        data.put(Constants.BODY, message);
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageId());
        data.put(Constants.TITLE, user.nameToDisplay());
        data.put("phone_number", user.getPhoneNumber());
        data.put("userUid", user.getUid());
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;

    }

    public static boolean isCommand(GcmUpstreamMessage input) {
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
        return message.split(",");
    }


    public static Map<String, Object> generateUserMutedResponseData(MessageSourceAccessor messageSourceAccessor,GcmUpstreamMessage input, Group group) {
        String groupUid = (String) input.getData().get(Constants.GROUP_UID);
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.chat.muted");
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.GROUP_UID, groupUid);
        data.put(Constants.GROUP_NAME, group.getGroupName());
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageId());
        data.put(Constants.TITLE, "Grassroot");
        data.put(Constants.BODY, responseMessage);
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;
    }

    public static Map<String, Object> generateInvalidCommandResponseData(MessageSourceAccessor messageSourceAccessor,GcmUpstreamMessage input, Group group) {
        String groupUid = (String) input.getData().get(Constants.GROUP_UID);
        String messageId = UIDGenerator.generateId().concat(String.valueOf(System.currentTimeMillis()));
        String responseMessage = messageSourceAccessor.getMessage("gcm.xmpp.command.invalid");
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.GROUP_UID, groupUid);
        data.put(Constants.GROUP_NAME, group.getGroupName());
        data.put("messageId", messageId);
        data.put("messageUid", input.getMessageId());
        data.put(Constants.TITLE, "Grassroot");
        data.put(Constants.BODY, responseMessage);
        data.put(Constants.ENTITY_TYPE, AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;
    }


}


