package za.org.grassroot.messaging.domain;

/**
 * @author Lesetse Kimwaga
 */
public class Message {

    private String messageId;
    private String body;

    public Message(String messageId, String body) {
        this.messageId = messageId;
        this.body = body;
    }

    public Message() {
    }

    public Message withBody(String body) {
        this.body = body;
        return this;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

}
