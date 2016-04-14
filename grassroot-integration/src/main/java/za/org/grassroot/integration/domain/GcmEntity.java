package za.org.grassroot.integration.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by paballo on 2016/04/05.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GcmEntity {

    private String message_id;
    private String to;
    private String collapseKey;
    private Long time_to_live = 300L; //5 minutes
    private static final boolean delayWhileIdle = false;
    private static final boolean deliveryReceiptRequested =true;
    private Object data;
    private Object notification;
    private String message_type;


    public GcmEntity(){

    }

    public GcmEntity(String messageId, String to, String collapseKey, Object data){
        this.message_id = messageId;
        this.to = to;
        this.collapseKey = collapseKey;
        this.data =data;

    }
    public GcmEntity(String messageId, String to, String messageType){
        this.message_id = messageId;
        this.to = to;
        this.message_type = messageType;
    }

    public GcmEntity(String messageId, String to, String collapseKey, Object data, Object notification){
        this.message_id = messageId;
        this.to = to;
        this.collapseKey = collapseKey;
        this.data =data;
        this.notification=notification;
    }


    public String getMessage_id() {
        return message_id;
    }

    public Object getNotification() {
        return notification;
    }

    public String getTo() {
        return to;
    }

    public String getCollapseKey() {
        return collapseKey;
    }

    public Long getTime_to_live() {
        return time_to_live;
    }

    public static boolean isDelayWhileIdle() {
        return delayWhileIdle;
    }

    public static boolean isDeliveryReceiptRequested() {
        return deliveryReceiptRequested;
    }

    public Object getData() {
        return data;
    }

    public String getMessage_type() {
        return message_type;
    }
}