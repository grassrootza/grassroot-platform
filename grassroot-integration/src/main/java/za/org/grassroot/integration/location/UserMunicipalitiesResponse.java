package za.org.grassroot.integration.location;

import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@ApiModel
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserMunicipalitiesResponse {
    private Map<String,Municipality> userMunicipalities;
    private List<String> notYetCachedUids;
}
