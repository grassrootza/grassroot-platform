package za.org.grassroot.webapp.controller.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.messaging.CreateJwtTokenRequest;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.JwtType;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.UsernamePasswordLoginFailedException;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.AndroidAuthToken;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@Api("/api/auth")
@RequestMapping("/api/auth")
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
    @ApiOperation(value = "Login using otp and retrieve a JWT token", notes = "The JWT token is returned as a string in the 'data' property")
    public ResponseEntity<ResponseWrapper> login(@RequestParam("phoneNumber")String phoneNumber,
                                                 @RequestParam("otp") String otp,
                                                 @RequestParam(value = "durationMillis", required = false) Long durationMillis) {
        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            passwordTokenService.validateOtp(msisdn, otp);

            // get the user object
            User user = userService.findByInputNumber(msisdn);

            // Generate a token for the user (for the moment assuming it is Android client)
            CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT);
            if (durationMillis != null && durationMillis != 0) {
                tokenRequest.setShortExpiryMillis(durationMillis);
            }
            String token = jwtService.createJwt(tokenRequest);

            // Assemble response entity
            AndroidAuthToken response = new AndroidAuthToken(user, token);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);
        } catch (InvalidOtpException e) {
           logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }

    }


    @ApiOperation(value = "Login using password and retrieve a JWT token", notes = "The JWT token is returned as a string in the 'data' property")
    @RequestMapping(value = "/login-password", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> webLogin(@RequestParam("phoneNumber") String phoneNumber,
                                                    @RequestParam("password") String password) {
        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            passwordTokenService.validatePassword(msisdn, password);

            // get the user object
            User user = userService.findByInputNumber(msisdn);

            // Generate a token for the user (for the moment assuming it is Android client)
            CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT);

            String token = jwtService.createJwt(tokenRequest);

            // Assemble response entity
            AndroidAuthToken response = new AndroidAuthToken(user, token);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);

        } catch (UsernamePasswordLoginFailedException e) {
            logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_PASSWORD);
        }

    }

    @RequestMapping(value = "/token/validate", method = RequestMethod.GET)
    @ApiOperation(value = "Validate whether a JWT token is available", notes = "Returns TOKEN_STILL_VALID in 'message', or " +
            "else 'INVALID_TOKEN'")
    public ResponseEntity<ResponseWrapper> validateToken(@RequestParam("token") String token) {
        boolean isJwtTokenValid = jwtService.isJwtTokenValid(token);
        if (isJwtTokenValid) {
            return RestUtil.messageOkayResponse(RestMessage.TOKEN_STILL_VALID);
        } else {
            return RestUtil.errorResponse(HttpStatus.EXPECTATION_FAILED, RestMessage.INVALID_TOKEN);
        }
    }

    @RequestMapping(value = "/token/refresh", method = RequestMethod.GET)
    @ApiOperation(value = "Refresh JWT token", notes = "Try to refresh an old or expired token, responds with " +
            "a new token as a string (in the 'data' property) if the old token is within the refresh window, or a bad request " +
            "if the token is still old")
    public ResponseEntity<ResponseWrapper> refreshToken(@RequestParam("oldToken")String oldToken,
                                                        @RequestParam(value = "durationMillis", required = false) Long durationMillis) {
        String newToken = jwtService.refreshToken(oldToken, JwtType.ANDROID_CLIENT, durationMillis);
        if (newToken != null) {
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, newToken);
        } else {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TOKEN_EXPIRED);
        }
    }
}