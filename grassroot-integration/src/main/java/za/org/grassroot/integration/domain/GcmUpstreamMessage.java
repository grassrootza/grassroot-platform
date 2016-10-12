package za.org.grassroot.integration.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/05/10.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GcmUpstreamMessage implements IncomingChatMessage {

    private String from;

    private String category;

    private Map<String, Object> data = new HashMap<>();

    private String to;

    @JsonProperty("message_id")
    private String messageId;


    @JsonProperty("message_type")
    private String messageType;

    @Override
    public String getFrom() {
        return from;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public String getTo() {
        return to;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public String getMessageType() {
        return messageType;
    }
}