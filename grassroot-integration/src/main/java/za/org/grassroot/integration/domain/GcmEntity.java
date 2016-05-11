package za.org.grassroot.integration.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Created by paballo on 2016/04/05.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GcmEntity {

    @JsonProperty("message_id")
    private String messageId;
    private String to;
    @JsonProperty("collapse_key")
    private String collapseKey;
    @JsonProperty("time_to_live")
    private Long timeToLive = 300L; //5 minutes
    @JsonProperty("delay_while_idle")
    private boolean delayWhileIdle = false;
    @JsonProperty("delivery_receipt_requested")
    private boolean deliveryReceiptRequested =true;
    private Map<String,Object> notification;
    private Map<String,Object> data;
    @JsonProperty("message_type")
    private String messageType;

    public GcmEntity() {
    }

    public GcmEntity(String messageId, String to, String messageType){
        this.messageId = messageId;
        this.to = to;
        this.messageType = messageType;
    }

    public GcmEntity(String messageId, String to, String collapseKey, Map<String,Object> data, Map<String,Object> notification){
        this.messageId = messageId;
        this.to = to;
        this.collapseKey = collapseKey;
        this.data =data;
        this.notification=notification;
    }


    public String getMessageId() {
        return messageId;
    }

    public Map<String,Object> getNotification() {
        return notification;
    }

    public String getTo() {
        return to;
    }

    public String getCollapseKey() {
        return collapseKey;
    }

    public Long getTimeToLive() {
        return timeToLive;
    }

    public boolean isDelayWhileIdle() {
        return delayWhileIdle;
    }

    public boolean isDeliveryReceiptRequested() {
        return deliveryReceiptRequested;
    }

    public Map<String,Object> getData() {
        return data;
    }

    public String getMessageType() {
        return messageType;
    }
}
