package za.org.grassroot.webapp.controller.rest.user;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/user/related") @Api("/v2/api/user/related")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class RelatedUserController extends BaseRestController {

    private UserManagementService userService;

    public RelatedUserController(JwtService jwtService, UserManagementService userManagementService) {
        super(jwtService, userManagementService);
        this.userService = userManagementService;
    }

    @RequestMapping(value = "/user/names", method = RequestMethod.GET)
    public @ResponseBody
    List<RelatedUserDTO> retrieveUserGraphNames(@RequestParam String fragment, HttpServletRequest request) {
        return userService.findRelatedUsers(getUserFromRequest(request), fragment)
                .stream()
                .map(RelatedUserDTO::new)
                .collect(Collectors.toList());
    }

}
