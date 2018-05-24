package za.org.grassroot.integration.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter @Setter @NoArgsConstructor @ToString
public class TownLookupResult {

    @JsonProperty("description")
    private String description;

    @JsonProperty("town_name")
    private String townName;

    @JsonProperty("postal_code")
    private String postalCode;

    @JsonProperty("province")
    private String province;

    @JsonProperty("place_id")
    private String placeId;

    @JsonProperty("lowest_type")
    private String placeType;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("latitude")
    private Double latitude;
}
