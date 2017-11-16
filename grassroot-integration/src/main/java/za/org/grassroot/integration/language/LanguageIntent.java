package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter @AllArgsConstructor @ToString
public class LanguageIntent {

    private String name;
    private double confidence;

}
