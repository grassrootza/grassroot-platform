package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@ApiModel
@Getter @AllArgsConstructor
public class NluResponse {

    @JsonProperty("text")
    private String originalText;
    private LanguageIntent intent;
    private List<LanguageEntity> entities;

}
