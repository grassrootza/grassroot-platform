package za.org.grassroot.integration.language;

import com.google.protobuf.ByteString;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NluBroker {

    NluParseResult parseText(String text, String conversationUid);

    List<ConvertedSpeech> speechToText(MultipartFile file, String encoding, int sampleRate);

    NluParseResult speechToIntent(MultipartFile file, String encoding, int sampleRate);

}
