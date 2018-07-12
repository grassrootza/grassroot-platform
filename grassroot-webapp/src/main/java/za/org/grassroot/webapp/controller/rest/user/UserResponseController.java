package za.org.grassroot.webapp.controller.rest.user;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/user/pending") @Api("/v2/api/user/pending")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class UserResponseController extends BaseRestController {

    private final UserResponseBroker userResponseBroker;

    public UserResponseController(JwtService jwtService, UserManagementService userManagementService, UserResponseBroker userResponseBroker) {
        super(jwtService, userManagementService);
        this.userResponseBroker = userResponseBroker;
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResponseEntity<PendingResponseDTO> nextPendingResponse(HttpServletRequest request) {
        final String userId = getUserIdFromRequest(request);
        EntityForUserResponse entity = userResponseBroker.checkForEntityForUserResponse(userId, true);
        return ResponseEntity.ok(entity == null ? new PendingResponseDTO() : new PendingResponseDTO(entity));
    }

}
