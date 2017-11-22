package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter @AllArgsConstructor @ToString
public class LanguageEntity {

    @JsonProperty("entity")
    private String entityType;

    private String value;

    private int start;
    private int end;

}
