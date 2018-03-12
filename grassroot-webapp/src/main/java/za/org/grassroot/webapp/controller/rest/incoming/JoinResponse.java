package za.org.grassroot.webapp.controller.rest.incoming;

import lombok.AllArgsConstructor;
import lombok.Getter;
import za.org.grassroot.core.dto.UserFullDTO;

@AllArgsConstructor @Getter
public class JoinResponse {

    String validationCode;
    UserFullDTO user;

}
