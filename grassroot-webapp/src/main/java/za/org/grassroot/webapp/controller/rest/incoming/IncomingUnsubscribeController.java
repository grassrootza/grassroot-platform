package za.org.grassroot.webapp.controller.rest.incoming;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Api("/v2/api/inbound/unsubscribe/")
@RestController @Grassroot2RestController
@RequestMapping("/v2/api/inbound/unsubscribe/")
public class IncomingUnsubscribeController extends BaseRestController {

    private final GroupBroker groupBroker;
    private final PasswordTokenService tokenService;

    public IncomingUnsubscribeController(JwtService jwtService, UserManagementService userManagementService, GroupBroker groupBroker, PasswordTokenService tokenService) {
        super(jwtService, userManagementService);
        this.groupBroker = groupBroker;
        this.tokenService = tokenService;
    }

    @RequestMapping(value = "{groupUid}/{userUid}/{token}", method = RequestMethod.GET)
    public ResponseEntity unsubscribeFromGroup(@PathVariable String groupUid, @PathVariable String userUid,
                                               @PathVariable String token, HttpServletRequest request) {
        final String requestUserUid = getUserIdFromRequest(request);
        if (!StringUtils.isEmpty(requestUserUid) && !requestUserUid.equals(userUid)) {
            throw new AccessDeniedException("Error! Looks like spoofing attempt");
        }
        tokenService.validateEntityResponseCode(userUid, groupUid, token);
        groupBroker.unsubscribeMember(userUid, groupUid);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "name/{groupUid}/{userUid}/{token}", method = RequestMethod.GET)
    public ResponseEntity fetchGroupName(@PathVariable String groupUid, @PathVariable String userUid,
                                         @PathVariable String token) {
        tokenService.validateEntityResponseCode(userUid, groupUid, token);
        return ResponseEntity.ok(groupBroker.load(groupUid).getName());
    }

}
