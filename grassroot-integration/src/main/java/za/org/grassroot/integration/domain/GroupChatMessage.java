package za.org.grassroot.integration.domain;

import java.util.Map;

/**
 * Created by luke on 2016/10/12.
 */
public interface GroupChatMessage {

    Map<String, Object> getData();

    String getFrom();

    String getTo();

    String getMessageUid();

    String getMessageType();

}
