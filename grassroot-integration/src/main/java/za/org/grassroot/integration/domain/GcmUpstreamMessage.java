package za.org.grassroot.integration.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by paballo on 2016/05/10.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GcmUpstreamMessage {

    private String from;

    private String category;

    private Map<String, Object> data = new HashMap<>();

    private String to;

    private String message_id;


    private String message_type;


    public String getFrom() {
        return from;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getTo() {
        return to;
    }

    public String getMessage_id() {
        return message_id;
    }

    public String getMessage_type() {
        return message_type;
    }
}