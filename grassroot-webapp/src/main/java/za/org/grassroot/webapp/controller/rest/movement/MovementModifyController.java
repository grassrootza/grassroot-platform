package za.org.grassroot.webapp.controller.rest.movement;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.movement.MovementPermissionType;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.movement.MovementBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Set;

@RestController @Grassroot2RestController
@Api("/v2/api/movement") @Slf4j
@RequestMapping(value = "/v2/api/movement")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
@ConditionalOnProperty("grassroot.movements.enabled")
public class MovementModifyController extends BaseRestController {

    private final MovementBroker movementBroker;

    @Autowired
    public MovementModifyController(JwtService jwtService, UserManagementService userManagementService, MovementBroker movementBroker) {
        super(jwtService, userManagementService);
        this.movementBroker = movementBroker;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public ResponseEntity createMovement(HttpServletRequest request,
                                         @RequestParam String movementName,
                                         @RequestParam MovementPermissionType permissionType,
                                         @RequestParam(required = false) Set<String> memberUids) {
        final String userUid = getUserIdFromRequest(request);
        String movementUid = movementBroker.createMovement(userUid, movementName, permissionType);
        if (memberUids != null) {
            movementBroker.addMembers(userUid, movementUid, memberUids);
        }

        return ResponseEntity.ok().build(); // turn into a wrapper soon
    }

    @RequestMapping(value = "/add/member/{movementUid}", method = RequestMethod.POST)
    public ResponseEntity addMemberToMovement(HttpServletRequest request,
                                              @PathVariable String movementUid,
                                              @RequestParam String memberUid,
                                              @RequestParam(required = false) String movementRole) {
        movementBroker.addMembers(getUserIdFromRequest(request), movementUid, Collections.singleton(memberUid));
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/add/entity/{movementUid}/{entityType}", method = RequestMethod.POST)
    public ResponseEntity addEntityToMovement(HttpServletRequest request,
                                              @PathVariable String movementUid,
                                              @PathVariable JpaEntityType entityType,
                                              @RequestParam String entityUid) {
        if (JpaEntityType.GROUP.equals(entityType)) {
            movementBroker.addGroup(getUserIdFromRequest(request), movementUid, entityUid);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @RequestMapping(value = "/remove/member/{movementUid}", method = RequestMethod.POST)
    public ResponseEntity removeMemberFromMovement(HttpServletRequest request,
                                                   @PathVariable String movementUid,
                                                   @RequestParam String memberUid,
                                                   @RequestParam String reasonToLog) {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/remove/entity/{movementUid}/{entityType}", method = RequestMethod.POST)
    public ResponseEntity removeEntityFromMovement(HttpServletRequest request,
                                                   @PathVariable String movementUid,
                                                   @PathVariable JpaEntityType entityType,
                                                   @RequestParam String memberUid,
                                                   @RequestParam String reasonToLog) {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/update/member/role/{movementUid}", method = RequestMethod.POST)
    public ResponseEntity updateMemberRole(HttpServletRequest request,
                                           @PathVariable String movementUid,
                                           @RequestParam String memberUid,
                                           @RequestParam String memberRole) {
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/update/member/annotate/{movementUid}", method = RequestMethod.POST)
    public ResponseEntity tagMember(HttpServletRequest request,
                                    @PathVariable String movementUid,
                                    @RequestParam String memberUid,
                                    @RequestParam Set<String> tags) {
        return ResponseEntity.ok().build();
    }

}
