package za.org.grassroot.webapp.controller.rest.authentication;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.VerificationCodeType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.messaging.CreateJwtTokenRequest;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.JwtType;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.InvalidTokenException;
import za.org.grassroot.services.exception.UserExistsException;
import za.org.grassroot.services.exception.UsernamePasswordLoginFailedException;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.exception.InterfaceNotOpenException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.AuthorizationResponseDTO;
import za.org.grassroot.webapp.model.rest.AuthorizedUserDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.Arrays;
import java.util.List;

@RestController
@Api("/api/auth")
@RequestMapping("/api/auth")
public class AuthenticationController {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private static final List<UserInterfaceType> alphaInterfaces =
            Arrays.asList(UserInterfaceType.ANGULAR, UserInterfaceType.ANDROID_2);

    private final JwtService jwtService;
    private final PasswordTokenService passwordTokenService;
    private final UserManagementService userService;
    private final MessagingServiceBroker messagingServiceBroker;
    private final Environment environment;

    @Autowired
    public AuthenticationController(JwtService jwtService, PasswordTokenService passwordTokenService,
                                    UserManagementService userService, MessagingServiceBroker messagingServiceBroker,
                                    Environment environment) {
        this.jwtService = jwtService;
        this.passwordTokenService = passwordTokenService;
        this.userService = userService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.environment = environment;
    }

    private void checkRegistrationOpen(UserInterfaceType interfaceType) {
        if (!environment.acceptsProfiles("localpg") && alphaInterfaces.contains(interfaceType)) {
            throw new InterfaceNotOpenException();
        }
    }

    private void checkUserHasAccess(String phoneOrEmail, UserInterfaceType interfaceType) {
        if (!environment.acceptsProfiles("localpg") && alphaInterfaces.contains(interfaceType) &&
                !userService.doesUserHaveStandardRole(phoneOrEmail, BaseRoles.ROLE_ALPHA_TESTER)) {
            throw new InterfaceNotOpenException();
        }
    }

    @RequestMapping(value = "/register", method = RequestMethod.GET)
    @ApiOperation(value = "Start new user registration using username, phone number and password", notes = "Short lived token is returned as a string in the 'data' property")
    public ResponseEntity<ResponseWrapper> register(@RequestParam("phoneNumber") String phoneNumber,
                                                    @RequestParam("displayName") String displayName,
                                                    @RequestParam("password") String password,
                                                    @RequestParam(required = false) UserInterfaceType type) {
        checkRegistrationOpen(type == null ? UserInterfaceType.ANDROID_2 : type);
        try {
            if (!ifExists(phoneNumber)) {
                phoneNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
                logger.info("Creating a verifier for a new user with phoneNumber ={}", phoneNumber);
                String tokenCode = temporaryTokenSend(
                        userService.generateAndroidUserVerifier(phoneNumber, displayName, password),
                        phoneNumber, "Registration confirmation code: ");

                return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, tokenCode);
            } else {
                logger.info("Creating a verifier for user with phoneNumber ={}, user already exists.", phoneNumber);
                return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS);
            }
        } catch (InvalidPhoneNumberException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_MSISDN);
        }
    }

    @RequestMapping(value = "/register/verify/{phoneNumber}/{code}", method = RequestMethod.GET)
    @ApiOperation(value = "Finish new user registration using otp password", notes = "User data and JWT token is returned as AuthorizedUserDTO object in the 'data' property")
    public ResponseEntity<ResponseWrapper> verifyRegistration(@PathVariable("phoneNumber") String phoneNumber,
                                                              @PathVariable("code") String otpEntered,
                                                              @RequestParam(required = false) UserInterfaceType type) throws Exception {
        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        checkRegistrationOpen(type == null ? UserInterfaceType.ANDROID_2 : type);
        if (passwordTokenService.isShortLivedOtpValid(msisdn, otpEntered)) {
            logger.info("user dto and code verified, now creating user with phoneNumber={}", phoneNumber);

            UserDTO userDTO = userService.loadUserCreateRequest(msisdn);
            User user = userService.createAndroidUserProfile(userDTO);
            passwordTokenService.generateLongLivedAuthCode(user.getUid());
            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);

            CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT, user.getUid());

            String token = jwtService.createJwt(tokenRequest);

            // Assemble response entity
            AuthorizedUserDTO response = new AuthorizedUserDTO(user, token);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);
        } else {
            logger.info("Token verification for new user failed");
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }
    }

    @RequestMapping(value = "web/register", method = RequestMethod.GET)
    public AuthorizationResponseDTO registerWebUser(@RequestParam String username,
                                                    @RequestParam String phoneNumber,
                                                    @RequestParam String password) {
        checkRegistrationOpen(UserInterfaceType.ANGULAR);
        try {
            if (StringUtils.isEmpty(username))
                return new AuthorizationResponseDTO(RestMessage.INVALID_USERNAME);
            else if (StringUtils.isEmpty(phoneNumber))
                new AuthorizationResponseDTO(RestMessage.INVALID_MSISDN);
            else if (StringUtils.isEmpty(phoneNumber))
                return new AuthorizationResponseDTO(RestMessage.INVALID_PASSWORD);

            User newUser = User.makeEmpty();
            newUser.setDisplayName(username);
            newUser.setPhoneNumber(phoneNumber);
            newUser.setPassword(password);

            User user = userService.createUserWebProfile(newUser);
            String token = jwtService.createJwt(new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT, user.getUid()));
            AuthorizedUserDTO response = new AuthorizedUserDTO(user, token);

            return new AuthorizationResponseDTO(response);

        } catch (UserExistsException userException) {
            return new AuthorizationResponseDTO(RestMessage.USER_ALREADY_EXISTS);
        } catch (InvalidPhoneNumberException phoneNumberException) {
            return new AuthorizationResponseDTO(RestMessage.INVALID_MSISDN);
        }
    }



    @RequestMapping(value = "/reset-password-request", method = RequestMethod.GET)
    @ApiOperation(value = "Reset user password request otp", notes = "Short lived token is sent to user in the 'data' property")
    public ResponseEntity<ResponseWrapper> resetPasswordRequest(@RequestParam("phoneNumber") String phoneNumber) {
        try {
            if (ifExists(phoneNumber)) {
                final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
                String token = userService.regenerateUserVerifier(msisdn, false);
                temporaryTokenSend(token, msisdn, "Password reset confirmation code: ");
                return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, token);
            } else {
                logger.info("Password reset requested for non-existing user: {}", phoneNumber);
                return RestUtil.errorResponse(HttpStatus.GONE, RestMessage.USER_DOES_NOT_EXIST);
            }
        } catch (InvalidPhoneNumberException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_MSISDN);
        }
    }

    @RequestMapping(value = "/reset-password-confirm", method = RequestMethod.GET)
    @ApiOperation(value = "Reset user password", notes = "New password is returned as a string in the 'data' property")
    public ResponseEntity<ResponseWrapper> resetPassword(@RequestParam("phoneNumber") String phoneNumber,
                                                         @RequestParam("password") String newPassword,
                                                         @RequestParam("code") String otpCode) {
        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);

            if (userService.userExist(msisdn)) {
                userService.resetUserPassword(msisdn, newPassword, otpCode);
                return RestUtil.okayResponseWithData(RestMessage.PASSWORD_RESET, newPassword);
            } else {
                logger.info("Password reset requested for non-existing user: {}", phoneNumber);
                return RestUtil.errorResponse(HttpStatus.NOT_FOUND, RestMessage.USER_DOES_NOT_EXIST);
            }
        } catch (InvalidTokenException e) {
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        } catch (InvalidPhoneNumberException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_MSISDN);
        }
    }


    @RequestMapping(value = "/login", method = RequestMethod.GET)
    @ApiOperation(value = "Login using otp and retrieve a JWT token", notes = "The JWT token is returned as a string in the 'data' property")
    public ResponseEntity<ResponseWrapper> login(@RequestParam("phoneNumber")String phoneNumber,
                                                 @RequestParam("otp") String otp,
                                                 @RequestParam(value = "durationMillis", required = false) Long durationMillis,
                                                 @RequestParam(required = false) UserInterfaceType interfaceType) {
        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            passwordTokenService.validateOtp(msisdn, otp);
            checkUserHasAccess(msisdn, interfaceType == null ? UserInterfaceType.ANDROID_2 : interfaceType);

            // get the user object
            User user = userService.findByInputNumber(msisdn);

            // Generate a token for the user (for the moment assuming it is Android client)
            CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT, user.getUid());
            if (durationMillis != null && durationMillis != 0) {
                tokenRequest.setShortExpiryMillis(durationMillis);
            }
            String token = jwtService.createJwt(tokenRequest);

            // Assemble response entity
            AuthorizedUserDTO response = new AuthorizedUserDTO(user, token);

            // Return the token on the response
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);
        } catch (InvalidOtpException e) {
           logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }

    }


    @ApiOperation(value = "Login using password and retrieve a JWT token", notes = "The JWT token is returned as a string in the 'data' property")
    @RequestMapping(value = "/login-password", method = RequestMethod.GET)
    public AuthorizationResponseDTO webLogin(@RequestParam("phoneNumber") String phoneNumber,
                                             @RequestParam("password") String password,
                                             @RequestParam(required = false) UserInterfaceType interfaceType) {
        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            passwordTokenService.validatePassword(msisdn, password);
            checkUserHasAccess(msisdn, interfaceType == null ? UserInterfaceType.ANGULAR : interfaceType);

            // get the user object
            User user = userService.findByInputNumber(msisdn);

            // Generate a token for the user (for the moment assuming it is Android client - Angular uses same params)
            CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.ANDROID_CLIENT, user.getUid());

            String token = jwtService.createJwt(tokenRequest);

            // Assemble response entity
            AuthorizedUserDTO response = new AuthorizedUserDTO(user, token);

            // Return the token on the response
            return new AuthorizationResponseDTO(response);
        } catch (UsernamePasswordLoginFailedException e) {
            logger.error("Failed to generate authentication token for:  " + phoneNumber);
            return new AuthorizationResponseDTO(RestMessage.INVALID_PASSWORD);
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


    private String temporaryTokenSend(String token, String destinationNumber, String messagePrefix) {
        if (environment.acceptsProfiles("production")) {
            if (token != null) {
                messagingServiceBroker.sendPrioritySMS(messagePrefix + token, destinationNumber);
            } else {
                logger.warn("Did not send verification message. No system messaging configuration found.");
            }
            return "";
        } else {
            return token;
        }
    }


    private boolean ifExists(String phoneNumber) {
        return userService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
    }
}