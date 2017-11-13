package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class LanguageEntity {

    @JsonProperty("entity")
    private String entityType;

    private String value;

    private int start;
    private int end;

}
