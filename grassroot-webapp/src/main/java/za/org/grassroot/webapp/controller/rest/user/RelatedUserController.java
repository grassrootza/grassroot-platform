package za.org.grassroot.webapp.controller.rest.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/api/user/related")
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
