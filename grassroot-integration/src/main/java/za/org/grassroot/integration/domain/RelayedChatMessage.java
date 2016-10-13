package za.org.grassroot.integration.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/10/13.
 */
public class RelayedChatMessage implements GroupChatMessage {

    // GcmUpstreamMessage{messageType='null', from=fZAv7q3Rfb8:APA91bGjE0qASTOPZ4Kaqz4ExgBdf8aZCTzuzcNesG9AQm2FPwaYiC15cN-_blU-VWfnOb7oDkBzF8xe4CH65OouwspYDyMQ_Q3T6fdmk2V_O9v6g27mhGWnae2dbR5nVf1v-S7i3H-E, to=null, category=org.grassroot.android, }

    private final String category;
    private final String groupUid;
    private final String messageText;
    private final String localMsgUid;
    private final String senderPhone;
    private final String senderGcmKey;

    private final Map<String, Object> data;

    public static class ChatMessageBuilder {
        private String category;
        private String from;
        private String groupUid;
        private String messageText;
        private String messageUid;
        private String userPhone;

        public ChatMessageBuilder(String category) {
            this.category = category;
        }

        public ChatMessageBuilder to(String groupUid) {
            this.groupUid = groupUid;
            return this;
        }

        public ChatMessageBuilder text(String text) {
            this.messageText = text;
            return this;
        }

        public ChatMessageBuilder from(String from) {
            this.from = from;
            return this;
        }

        public ChatMessageBuilder senderPhone(String senderPhone) {
            this.userPhone = senderPhone;
            return this;
        }

        public ChatMessageBuilder messageUid(String messageUid) {
            this.messageUid = messageUid;
            return this;
        }

        public RelayedChatMessage build() {
            return new RelayedChatMessage(category, groupUid, messageText, messageUid, from, userPhone);
        }

    }

    private RelayedChatMessage(String category, String toGroupUid, String text, String localUid, String senderGcmKey, String senderPhone) {
        this.category = category;
        this.groupUid = toGroupUid;
        this.messageText = text;
        this.localMsgUid = localUid;
        this.senderGcmKey = senderGcmKey;
        this.senderPhone = senderPhone;

        // todo : use constants / enums in here instead of literals
        this.data = new HashMap<>();
        data.put("phoneNumber", senderPhone);
        data.put("action", "CHAT");
        data.put("time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        data.put("message", messageText);
        data.put("groupUid", groupUid);
        data.put("entity_type", AndroidClickActionType.CHAT_MESSAGE);
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public String getFrom() {
        return senderGcmKey;
    }

    @Override
    public String getTo() {
        return groupUid;
    }

    @Override
    public String getMessageUid() {
        return localMsgUid;
    }

    @Override
    public String getMessageType() {
        return null;
    }
}
