package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class LanguageIntent {

    private String name;
    private double confidence;

}
