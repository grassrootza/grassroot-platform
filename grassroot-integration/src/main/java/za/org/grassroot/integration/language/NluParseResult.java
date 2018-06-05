package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.*;

import java.util.List;

@ApiModel
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class NluParseResult {

    @JsonProperty("text")
    private String originalText;
    private LanguageIntent intent;
    private List<LanguageEntity> entities;

}
