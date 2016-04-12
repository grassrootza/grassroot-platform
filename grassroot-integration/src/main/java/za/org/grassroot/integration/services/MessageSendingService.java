package za.org.grassroot.integration.services;

import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.MessageProtocol;

/**
 * Created by luke on 2015/09/09.
 */
public interface MessageSendingService {

     String sendMessage(String message, String destination, MessageProtocol messageProtocol);
     void sendMessage(Notification notification);
     void sendMessage(String destination, Notification notification);
     //void sendMessage(String destination, Object object);





}
