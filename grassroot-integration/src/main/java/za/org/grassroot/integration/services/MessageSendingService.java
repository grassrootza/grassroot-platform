package za.org.grassroot.integration.services;

import za.org.grassroot.integration.domain.MessageProtocol;

import java.util.List;

/**
 * Created by luke on 2015/09/09.
 */
public interface MessageSendingService {

    public String sendMessage(String message, String destination, MessageProtocol messageProtocol);

    public String sendMessage(Object payload, List<String> destination, MessageProtocol messageProtocol);

}
