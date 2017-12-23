package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.webapp.enums.RestMessage;

@Getter
@Setter
public class AuthorizationResponseDTO {

    private RestMessage errorCode = null;
    private AuthorizedUserDTO user;

    @JsonCreator
    public AuthorizationResponseDTO(@JsonProperty("user") AuthorizedUserDTO user) {
        this.user = user;
    }

    @JsonCreator
    public AuthorizationResponseDTO(@JsonProperty("errorCode") RestMessage errorCode) {
        this.errorCode = errorCode;
    }
}
