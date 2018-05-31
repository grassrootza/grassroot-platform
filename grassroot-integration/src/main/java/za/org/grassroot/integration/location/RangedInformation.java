package za.org.grassroot.integration.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @ToString
public class RangedInformation {

    @JsonProperty("distance")
    private long distance;

    @JsonProperty("key")
    private String information;

}
