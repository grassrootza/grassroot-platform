package za.org.grassroot.webapp.controller.rest.whatsapp;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/whatsapp") @Api("/v2/api/whatsapp")
@PreAuthorize("hasRole('ROLE_SYSTEM_CALL')")
public class WhatsAppRelatedController extends BaseController {

    public WhatsAppRelatedController(UserManagementService userManagementService, PermissionBroker permissionBroker) {
        super(userManagementService, permissionBroker);
    }

    @RequestMapping(value = "/user/id", method = RequestMethod.POST)
    public ResponseEntity fetchUserId(String msisdn) {
        User user = userManagementService.loadOrCreate(msisdn);
        return ResponseEntity.ok(user.getUid());
    }
}
