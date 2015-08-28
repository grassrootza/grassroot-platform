package za.org.grassroot.messaging.domain;

/**
 * @author Lesetse Kimwaga
 */
public class MessagePublishRequest {

    private Message message;
    private Destination destination;
    private MessageProtocol messageProtocol;

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Destination getDestination() {
        return destination;
    }

    public void setDestination(Destination destination) {
        this.destination = destination;
    }

    public MessageProtocol getMessageProtocol() {
        return messageProtocol;
    }

    public void setMessageProtocol(MessageProtocol messageProtocol) {
        this.messageProtocol = messageProtocol;
    }
}
