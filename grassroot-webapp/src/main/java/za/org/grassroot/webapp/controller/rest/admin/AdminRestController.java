package za.org.grassroot.webapp.controller.rest.admin;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.dto.UserFullDTO;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

@RestController
@Slf4j
@Api("/api/admin")
@RequestMapping(value = "/api/admin")
public class AdminRestController extends BaseRestController{

    private final AdminService adminService;
    private final UserManagementService userManagementService;
    private final MessagingServiceBroker messagingServiceBroker;
    private final PasswordTokenService passwordTokenService;

    public AdminRestController(UserManagementService userManagementService,
                               JwtService jwtService,
                               AdminService adminService,
                               MessagingServiceBroker messagingServiceBroker,
                               PasswordTokenService passwordTokenService){
        super(jwtService,userManagementService);
        this.adminService = adminService;
        this.userManagementService = userManagementService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.passwordTokenService = passwordTokenService;
    }

    @RequestMapping(value = "/users/load",method = RequestMethod.GET)
    public ResponseEntity<Boolean> loadUser(@RequestParam() String lookupTerm,
                                                HttpServletRequest request){
        boolean isPhoneNumber = PhoneNumberUtil.testInputNumber(lookupTerm);
        if (!isPhoneNumber && !EmailValidator.getInstance().isValid(lookupTerm)) {
            throw new NoSuchUserException("Enter email or phone number: " + lookupTerm);
        }
        boolean found = false;
        User user = userManagementService.findByUsernameLoose(lookupTerm);
        if(user != null){
            found = true;
            String token = userManagementService.regenerateUserVerifier(user.getUsername(), false);
            if(token != null){
                messagingServiceBroker.sendPrioritySMS("User opt-out code:" + token,user.getPhoneNumber());
            }
        }
        return ResponseEntity.ok(found);
    }

    @RequestMapping(value = "/user/optout",method = RequestMethod.POST)
    public ResponseEntity<String> userOptout(@RequestParam String otpEntered,
                                             HttpServletRequest request){
        if (!passwordTokenService.isShortLivedOtpValid(getUserFromRequest(request).getPhoneNumber(), otpEntered)) {
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }
        adminService.removeUserFromAllGroups(getUserIdFromRequest(request), getUserIdFromRequest(request));
        return ResponseEntity.ok("SUCCESS");
    }
}
