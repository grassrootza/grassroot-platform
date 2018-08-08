package za.org.grassroot.webapp.controller.rest.movement;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.movement.Movement;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.movement.MovementBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/v2/api/movement") @Slf4j
@RequestMapping(value = "/v2/api/movement") @PreAuthorize("hasRole('ROLE_FULL_USER')")
@ConditionalOnProperty("grassroot.movements.enabled")
public class MovementFetchController extends BaseRestController {

    private final MovementBroker movementBroker;

    public MovementFetchController(JwtService jwtService, UserManagementService userManagementService, MovementBroker movementBroker) {
        super(jwtService, userManagementService);
        this.movementBroker = movementBroker;
    }

    @RequestMapping(value = "/fetch", method = RequestMethod.GET)
    public ResponseEntity fetchUserMovements(HttpServletRequest request) {
        List<Movement> movementList = movementBroker.loadUserMovements(getUserIdFromRequest(request));
        return ResponseEntity.ok(movementList.stream().map(MovementWrapper::new).collect(Collectors.toList()));
    }

    @RequestMapping(value = "/fetch/{movementUid}", method = RequestMethod.GET)
    public ResponseEntity fetchDetailsOnMovement(HttpServletRequest request,
                                                 @PathVariable String movementUid) {
        return ResponseEntity.ok(new MovementWrapper(movementBroker.load(movementUid)));
    }

}
