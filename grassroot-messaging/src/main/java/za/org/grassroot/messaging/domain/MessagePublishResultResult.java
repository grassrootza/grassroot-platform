package za.org.grassroot.messaging.domain;

/**
 * @author Lesetse Kimwaga
 */
public class MessagePublishResultResult {

    private String messageId;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public MessagePublishResultResult withMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }
}
