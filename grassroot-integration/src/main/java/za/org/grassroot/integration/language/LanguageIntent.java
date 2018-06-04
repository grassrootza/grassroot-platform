package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class LanguageIntent {

    private String name;
    private double confidence;

}
