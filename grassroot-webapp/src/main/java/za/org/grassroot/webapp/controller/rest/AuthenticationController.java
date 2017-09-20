package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.messaging.CreateJwtTokenRequest;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.JwtType;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.InvalidPasswordException;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.AndroidAuthToken;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private final JwtService jwtService;
    private final PasswordTokenService passwordTokenService;
    private final UserManagementService userService;

    @Autowired
    public AuthenticationController(JwtService jwtService, PasswordTokenService passwordTokenService, UserManagementService userService) {
        this.jwtService = jwtService;
        this.passwordTokenService = passwordTokenService;
        this.userService = userService;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> login(@RequestParam("phoneNumber")String phoneNumber,
                                                 @RequestParam("otp")String otp,
                                                 @RequestParam("clientType") String clientType) {
        try {
            // authenticate user before issuing token
            passwordTokenService.validateOtp(phoneNumber, otp);

            // get the user object
            User user = userService.findByInputNumber(phoneNumber);

            // Generate a token for the user (for the moment assuming it is Android client)
            String token = jwtService.createJwt(new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT));

            // Assemble response entity
            AndroidAuthToken response = new AndroidAuthToken(user, token);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);
        } catch (InvalidOtpException e) {
           logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }

    }


    @RequestMapping(value = "/web-login", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> webLogin(@RequestParam("phoneNumber") String phoneNumber,
                                                    @RequestParam("password") String password) {
        try {
            // authenticate user before issuing token
            passwordTokenService.validatePassword(phoneNumber, password);

            // get the user object
            User user = userService.findByInputNumber(phoneNumber);

            // Generate a token for the user
            String token = jwtService.createJwt(new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT));

            // Assemble response entity
            AndroidAuthToken response = new AndroidAuthToken(user, token);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);
        } catch (InvalidPasswordException e) {
            logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_PASSWORD);
        }

    }

    @RequestMapping(value = "/token/validate", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> validateToken(@RequestParam("token") String token) {
        boolean isJwtTokenValid = jwtService.isJwtTokenValid(token);
        if (isJwtTokenValid) {
            return RestUtil.messageOkayResponse(RestMessage.TOKEN_STILL_VALID);
        } else {
            return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.INVALID_TOKEN);
        }
    }

    @RequestMapping(value = "/token/refresh", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> refreshToken(@RequestParam("oldToken")String oldToken) {
        String newToken = jwtService.refreshToken(oldToken, JwtType.ANDROID_CLIENT);
        if (newToken != null) {
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, newToken);
        } else {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TOKEN_EXPIRED);
        }
    }
}
