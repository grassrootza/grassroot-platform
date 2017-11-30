package za.org.grassroot.integration.language;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class ConvertedSpeech {

    private String speech;
    private float confidence;

}
