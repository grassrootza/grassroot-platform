package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter @Setter @AllArgsConstructor @ToString @NoArgsConstructor
public class NluServerResponse {

    private String conversationUid;

    @JsonProperty("parsed")
    private NluParseResult result;

    @JsonProperty("date")
    private String dateTimeParsed;

}
