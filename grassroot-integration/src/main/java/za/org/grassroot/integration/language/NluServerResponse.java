package za.org.grassroot.integration.language;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter @AllArgsConstructor @ToString
public class NluServerResponse {

    private String conversationUid;

    @JsonProperty("parsed")
    private NluParseResult result;

    @JsonProperty("date")
    private String dateTimeParsed;

}
