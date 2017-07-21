package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.integration.messaging.CreateJwtTokenRequest;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.JwtType;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private final JwtService jwtService;
    private final PasswordTokenService passwordTokenService;

    @Autowired
    public AuthenticationController(JwtService jwtService, PasswordTokenService passwordTokenService) {
        this.jwtService = jwtService;
        this.passwordTokenService = passwordTokenService;
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> login(@RequestParam("phoneNumber")String phoneNumber,
                                                 @RequestParam("otp")String otp,
                                                 @RequestParam("clientType") String clientType) {
        try {
            // authenticate user before issuing token
            passwordTokenService.validateOtp(phoneNumber, otp);

            // Generate a token for the user (for the moment assuming it is Android client)
            String token = jwtService.createJwt(new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT));

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, token);
        } catch (InvalidOtpException e) {
           logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }

    }

    @RequestMapping(value = "/validateToken", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> validateToken(@RequestParam("token")String token) {
        boolean isJwtTokenValid = jwtService.isJwtTokenValid(token);
        if (isJwtTokenValid) {
            return RestUtil.messageOkayResponse(RestMessage.TOKEN_STILL_VALID);
        } else {
            return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.INVALID_TOKEN);
        }
    }

    @RequestMapping(value = "/refreshToken", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> refreshToken(@RequestParam("oldToken")String oldToken) {
        String newToken = jwtService.refreshToken(oldToken, JwtType.ANDROID_CLIENT);
        if (newToken != null) {
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, newToken);
        } else {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TOKEN_EXPIRED);
        }
    }
}
