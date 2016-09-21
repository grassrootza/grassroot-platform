package za.org.grassroot.integration.utils;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.integration.domain.AndroidClickActionType;
import za.org.grassroot.integration.domain.GcmUpstreamMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/09/20.
 */
public class MessageUtils {

    public static Map<String, Object> generateCommandResponseData(GcmUpstreamMessage input, Group group, String[] tokens) {
        String groupUid = (String) input.getData().get("groupUid");
        String messageId = UIDGenerator.generateId();
        String message = String.valueOf(input.getData().get("message"));
        Map<String, Object> data = new HashMap<>();
        data.put("groupUid", groupUid);
        data.put("groupName", group.getGroupName());
        data.put("id", messageId);
        data.put("uid", input.getMessageId());
        data.put("title", "Grassroot");
        if (message.contains("/meeting")) {
            String text =  "Do you want to call a meeting about %s on %s at %s?";
            data.put("type", "meeting");
            data.put("body", String.format(text,(Object[])tokens));
        }
        data.put("tokens", Arrays.asList(tokens));
        data.put("entity_type", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;

    }


    public static Map<String, Object> generateChatMessageData(GcmUpstreamMessage input, User user, Group group) {

        String message = (String) input.getData().get("message");
        Map<String, Object> data = new HashMap<>();
        String messageId = UIDGenerator.generateId();
        data.put("groupUid", group.getUid());
        data.put("groupName", group.getGroupName());
        data.put("groupIcon", group.getImageUrl());
        data.put("body", message);
        data.put("id", messageId);
        data.put("uid", input.getMessageId());
        data.put("title", user.nameToDisplay());
        data.put("phone_number", user.getPhoneNumber());
        data.put("userUid", user.getUid());
        data.put("entity_type", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("click_action", AndroidClickActionType.CHAT_MESSAGE.toString());
        data.put("time", input.getData().get("time"));

        return data;

    }

    public static boolean isCommand(GcmUpstreamMessage input) {
        String text = String.valueOf(input.getData().get("message"));
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


}


