package za.org.grassroot.integration.language;

import com.google.protobuf.ByteString;

import java.util.List;

public interface NluBroker {

    NluResponse parseText(String text, String conversationUid);

    List<ConvertedSpeech> speechToText(ByteString rawSpeech, String encoding, int sampleRate);

    NluResponse speechToIntent(ByteString rawSpeech, String encoding, int sampleRate);

}
