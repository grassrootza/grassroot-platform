package za.org.grassroot.webapp.controller.rest.admin;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.AdminService;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.Random;

@RestController
@Slf4j
@Api("/api/admin")
@RequestMapping(value = "/api/admin")
public class AdminRestController extends BaseRestController{

    private static final Random RANDOM = new SecureRandom();

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

    @RequestMapping(value = "/user/load",method = RequestMethod.GET)
    public ResponseEntity<String> loadUser(@RequestParam() String lookupTerm,
                                                HttpServletRequest request){
        User user = userManagementService.findByUsernameLoose(lookupTerm);
        String userUid = "";
        if(user != null){
            userUid = user.getUid();
            passwordTokenService.triggerOtp(getUserFromRequest(request));
        }
        return ResponseEntity.ok(userUid);
    }

    @RequestMapping(value = "/user/optout",method = RequestMethod.POST)
    public ResponseEntity<String> userOptout(@RequestParam String userToOptOutUid,
                                             @RequestParam String otpEntered,
                                             HttpServletRequest request){
        if (!passwordTokenService.isShortLivedOtpValid(getUserFromRequest(request).getPhoneNumber(), otpEntered)) {
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }
        adminService.removeUserFromAllGroups(getUserIdFromRequest(request), userToOptOutUid);
        return ResponseEntity.ok("SUCCESS");
    }

    @RequestMapping(value = "/user/pwd/reset",method = RequestMethod.POST)
    public ResponseEntity<String> updateUserPassword(@RequestParam String userToResetUid,
                                                     @RequestParam String otpEntered,
                                                     HttpServletRequest request){
        if (!passwordTokenService.isShortLivedOtpValid(getUserFromRequest(request).getPhoneNumber(), otpEntered)) {
            throw new AccessDeniedException("Error! Admin user did not validate with OTP");
        }

        String newPwd = generateRandomPwd();
        adminService.updateUserPassword(getUserIdFromRequest(request), userToResetUid, newPwd);
        //Sending the password to user
        User user = userManagementService.load(userToResetUid);
        if(user.hasPhoneNumber()){
            messagingServiceBroker.sendPrioritySMS("New Grassroot password:"+newPwd,getUserFromRequest(request).getPhoneNumber());
        }
        return ResponseEntity.ok("SUCCESS");
    }

    private String generateRandomPwd() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789+@";
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 8; i++){
            int index = (int)(RANDOM.nextDouble()*letters.length());
            password.append(letters.substring(index, index + 1));
        }

        return password.toString();
    }
}
