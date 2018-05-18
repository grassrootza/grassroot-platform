package za.org.grassroot.services.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
public class TownLookupResult {

    @JsonProperty("description")
    private String description;

    @JsonProperty("place_id")
    private String placeId;

    @JsonProperty("lowest_type")
    private String placeType;

}
