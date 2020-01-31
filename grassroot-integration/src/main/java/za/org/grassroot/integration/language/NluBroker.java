package za.org.grassroot.integration.language;

public interface NluBroker {

    NluParseResult parseText(String text, String conversationUid);

}
