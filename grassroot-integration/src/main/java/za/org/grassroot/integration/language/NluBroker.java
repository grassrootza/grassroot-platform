package za.org.grassroot.integration.language;

import com.google.protobuf.ByteString;

import java.util.List;

public interface NluBroker {

    NluParseResult parseText(String text, String conversationUid);

    List<ConvertedSpeech> speechToText(ByteString rawSpeech, String encoding, int sampleRate);

    NluParseResult speechToIntent(ByteString rawSpeech, String encoding, int sampleRate);

}
