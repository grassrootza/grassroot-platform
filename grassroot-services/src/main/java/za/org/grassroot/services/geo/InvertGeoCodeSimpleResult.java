package za.org.grassroot.services.geo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "place_id",
        "licence",
        "osm_type",
        "osm_id",
        "lat",
        "lon",
        "display_name",
        "boundingbox"
})
public class InvertGeoCodeSimpleResult {

    @JsonProperty("place_id")
    private String placeId;
    @JsonProperty("licence")
    private String licence;
    @JsonProperty("osm_type")
    private String osmType;
    @JsonProperty("osm_id")
    private String osmId;
    @JsonProperty("lat")
    private String lat;
    @JsonProperty("lon")
    private String lon;
    @JsonProperty("display_name")
    private String displayName;
    @JsonProperty("boundingbox")
    private List<String> boundingbox = null;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("place_id")
    public String getPlaceId() {
        return placeId;
    }

    @JsonProperty("place_id")
    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    @JsonProperty("licence")
    public String getLicence() {
        return licence;
    }

    @JsonProperty("licence")
    public void setLicence(String licence) {
        this.licence = licence;
    }

    @JsonProperty("osm_type")
    public String getOsmType() {
        return osmType;
    }

    @JsonProperty("osm_type")
    public void setOsmType(String osmType) {
        this.osmType = osmType;
    }

    @JsonProperty("osm_id")
    public String getOsmId() {
        return osmId;
    }

    @JsonProperty("osm_id")
    public void setOsmId(String osmId) {
        this.osmId = osmId;
    }

    @JsonProperty("lat")
    public String getLat() {
        return lat;
    }

    @JsonProperty("lat")
    public void setLat(String lat) {
        this.lat = lat;
    }

    @JsonProperty("lon")
    public String getLon() {
        return lon;
    }

    @JsonProperty("lon")
    public void setLon(String lon) {
        this.lon = lon;
    }

    @JsonProperty("display_name")
    public String getDisplayName() {
        return displayName;
    }

    @JsonProperty("display_name")
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @JsonProperty("boundingbox")
    public List<String> getBoundingbox() {
        return boundingbox;
    }

    @JsonProperty("boundingbox")
    public void setBoundingbox(List<String> boundingbox) {
        this.boundingbox = boundingbox;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return "InvertGeoCodeSimpleResult{" +
                "placeId='" + placeId + '\'' +
                ", licence='" + licence + '\'' +
                ", osmType='" + osmType + '\'' +
                ", osmId='" + osmId + '\'' +
                ", lat='" + lat + '\'' +
                ", lon='" + lon + '\'' +
                ", displayName='" + displayName + '\'' +
                ", boundingbox=" + boundingbox +
                ", additionalProperties=" + additionalProperties +
                '}';
    }
}