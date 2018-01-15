package za.org.grassroot.webapp.controller.android1;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.VerificationCodeType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.AuthWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ProfileSettingsDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.util.Locale;

/**
 * Created by paballo.
 */
@RestController
@Api("/api/user")
@RequestMapping(value = "/api/user")
public class UserRestController {

    private static final Logger log = LoggerFactory.getLogger(UserRestController.class);

    private final UserManagementService userManagementService;
    private final PasswordTokenService passwordTokenService;
    private final GeoLocationBroker geoLocationBroker;
    private final MessagingServiceBroker messagingServiceBroker;
    private final NotificationService notificationService;
    private final PermissionBroker permissionBroker;
    private final JwtService jwtService;
    private final Environment environment;

    @Autowired
    public UserRestController(UserManagementService userManagementService, PasswordTokenService passwordTokenService,
                              GeoLocationBroker geoLocationBroker, MessagingServiceBroker messagingServiceBroker, NotificationService notificationService,
                              PermissionBroker permissionBroker, JwtService jwtService, Environment environment) {
        this.userManagementService = userManagementService;
        this.passwordTokenService = passwordTokenService;
        this.geoLocationBroker = geoLocationBroker;
        this.messagingServiceBroker = messagingServiceBroker;
        this.notificationService = notificationService;
        this.permissionBroker = permissionBroker;
        this.jwtService = jwtService;
        this.environment = environment;
    }

    @RequestMapping(value = "/add/{phoneNumber}/{displayName}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> add(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("displayName") String displayName) {

        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            if (!ifExists(msisdn)) {
                log.info("Creating a verifier for a new user with phoneNumber ={}", phoneNumber);
                String tokenCode = temporaryTokenSend(userManagementService.generateAndroidUserVerifier(phoneNumber, displayName, null), msisdn, false);
                return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, tokenCode);
            } else {
                log.info("Creating a verifier for user with phoneNumber ={}, user already exists.", phoneNumber);
                return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS);
            }
        } catch (InvalidPhoneNumberException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_MSISDN);
        }
    }




    @RequestMapping(value = "/verify/resend/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> resendOtp(@PathVariable("phoneNumber") String phoneNumber) {
        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        try {
            final String tokenCode = temporaryTokenSend(userManagementService.regenerateUserVerifier(msisdn, true), msisdn, true); // will be empty in production
            return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, tokenCode);
        } catch (Exception e) {
            log.info("here is the error : " + e.toString());
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.OTP_REQ_BEFORE_ADD);
        }
    }

    @RequestMapping(value = "/verify/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> verify(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String otpEntered)
            throws Exception {

        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        if (passwordTokenService.isShortLivedOtpValid(msisdn, otpEntered)) {
            log.info("user dto and code verified, now creating user with phoneNumber={}", phoneNumber);

            UserDTO userDTO = userManagementService.loadUserCreateRequest(msisdn);
            User user = userManagementService.createAndroidUserProfile(userDTO);
            VerificationTokenCode token = passwordTokenService.generateLongLivedAuthCode(user.getUid());
            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);

            AuthWrapper authWrapper = AuthWrapper.create(true, token, user, false, 0); // by definition, no groups or notiifcations
            return new ResponseEntity<>(authWrapper, HttpStatus.OK);
        } else {
            log.info("Token verification for new user failed");
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }
    }

    @RequestMapping(value = "/login/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logon(@PathVariable("phoneNumber") String phoneNumber) {

        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            if (ifExists(msisdn)) {
                // this will send the token by SMS and return an empty string if in production, or return the token if on staging
                String token = temporaryTokenSend(userManagementService.generateAndroidUserVerifier(msisdn, null, null), msisdn, false);
                return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, token);
            } else {
                return RestUtil.errorResponse(HttpStatus.NOT_FOUND, RestMessage.USER_DOES_NOT_EXIST);
            }
        } catch (InvalidPhoneNumberException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_MSISDN);
        }
    }

    @RequestMapping(value = "/login/authenticate/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> authenticate(@PathVariable("phoneNumber") String phoneNumber,
                                                        @PathVariable("code") String token) {

        if (passwordTokenService.isShortLivedOtpValid(phoneNumber, token)) {
            log.info("User authentication successful for user with phoneNumber={}", phoneNumber);
            User user = userManagementService.findByInputNumber(phoneNumber);
            if (!user.hasAndroidProfile()) {
                userManagementService.createAndroidUserProfile(new UserDTO(user));
            }
            userManagementService.setMessagingPreference(user.getUid(), DeliveryRoute.ANDROID_APP); // todo : maybe move to gcm registration
            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);
            VerificationTokenCode longLivedToken = passwordTokenService.generateLongLivedAuthCode(user.getUid());
            boolean hasGroups = permissionBroker.countActiveGroupsWithPermission(user, null) != 0;
            int notificationCount = notificationService.countUnviewedAndroidNotifications(user.getUid());

            AuthWrapper authWrapper = AuthWrapper.create(false, longLivedToken, user, hasGroups, notificationCount);
            return new ResponseEntity<>(authWrapper, HttpStatus.OK);
        } else {
            log.info("Android: Okay invalid code supplied by user={}");
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }
    }



    @RequestMapping(value = "/connect/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> checkConnection(@PathVariable String phoneNumber,
                                                           @PathVariable String code) {
        // just load the user, to make sure exists (or it will return server error), and send back
        User user = userManagementService.findByInputNumber(phoneNumber);
        log.info("reconnected user : " + user.getPhoneNumber());
        return RestUtil.messageOkayResponse(RestMessage.USER_OKAY);
    }

    @RequestMapping(value = "/auth/extend/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> extendToken(@PathVariable String phoneNumber, @PathVariable String code) {
        try {
            boolean tokenRefreshed = passwordTokenService.extendAuthCodeIfExpiring(phoneNumber, code);
            return RestUtil.messageOkayResponse(tokenRefreshed ? RestMessage.TOKEN_EXTENDED : RestMessage.TOKEN_STILL_VALID);
        } catch (NullPointerException e) {
            log.error("Error extending token: {}", e.getMessage());
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.BAD_TOKEN_UPDATE);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/auth/refresh/initiate/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> initiateTokenRefresh(@PathVariable String phoneNumber) {
        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            // this will send the token by SMS and return an empty string if in production, or return the token if on staging
            String token = temporaryTokenSend(
                    userManagementService.regenerateUserVerifier(msisdn, false), msisdn, false);
            return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, token);
        } catch (InvalidPhoneNumberException|AccessDeniedException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.BAD_TOKEN_UPDATE);
        }
    }

    @RequestMapping(value = "/auth/refresh/verify/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> verifyTokenRefresh(@PathVariable String phoneNumber,
                                                              @RequestParam String otp) {
        if (passwordTokenService.isShortLivedOtpValid(phoneNumber, otp)) {
            User user = userManagementService.findByInputNumber(phoneNumber);
            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);
            VerificationTokenCode longLivedToken = passwordTokenService.generateLongLivedAuthCode(user.getUid());
            return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, longLivedToken.getCode());
        } else {
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }
    }

    @RequestMapping(value = "/logout/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logoutUser(@PathVariable String phoneNumber, @PathVariable String code) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        userManagementService.setMessagingPreference(user.getUid(), DeliveryRoute.SMS);
        passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.LONG_AUTH);
        return RestUtil.messageOkayResponse(RestMessage.USER_LOGGED_OUT);
    }

    @RequestMapping(value="/profile/settings/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getProfileSettings(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code){
        User user = userManagementService.findByInputNumber(phoneNumber);
        ProfileSettingsDTO profileSettingsDTO = new ProfileSettingsDTO(user.getDisplayName(), user.getLanguageCode(), user.getAlertPreference().toString());
        return RestUtil.okayResponseWithData(RestMessage.PROFILE_SETTINGS, profileSettingsDTO);
    }

    @RequestMapping(value = "/profile/settings/rename/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> renameUser(@PathVariable String phoneNumber, @PathVariable String code,
                                                      @RequestParam String displayName) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        userManagementService.updateDisplayName(user.getUid(), user.getUid(), displayName);
        return RestUtil.messageOkayResponse(RestMessage.PROFILE_SETTINGS_UPDATED);
    }

    /*
     note : it might be slightly more efficient to just have the integer (for notification priority) directly from the client, _but_ there is some
     uncertainty about how notification priorities will evolve, and integer meanings within core/services may shift, hence
     the use of strings for flexibility etc
      */
    @RequestMapping(value = "/profile/settings/notify/priority/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> changeNotificationPriority(@PathVariable String phoneNumber, @PathVariable String code,
                                                                      @RequestParam AlertPreference alertPreference) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        userManagementService.updateAlertPreferences(user.getUid(), alertPreference);
        return RestUtil.messageOkayResponse(RestMessage.PROFILE_SETTINGS_UPDATED);
    }

    @RequestMapping(value = "/profile/settings/language/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> changeUserLanguage(@PathVariable String phoneNumber, @PathVariable String code,
                                                              @RequestParam String language) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            Locale passedLocale = new Locale(language);
            log.info("received a passed locale ... here it is  :" + passedLocale.toString());
            userManagementService.updateUserLanguage(user.getUid(), passedLocale);
            return RestUtil.messageOkayResponse(RestMessage.PROFILE_SETTINGS_UPDATED);
        } catch (IllegalArgumentException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_LANG_CODE);
        }
    }

    @RequestMapping(value= "/location/{phoneNumber}/{code}/{latitude}/{longitude:.+}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logUserLocation(@PathVariable String phoneNumber, @PathVariable String code,
                                                           @PathVariable double latitude, @PathVariable double longitude) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        log.info("Recording a location! With longitude = {} and lattitude = {}, from path string", longitude, latitude);
        geoLocationBroker.logUserLocation(user.getUid(), latitude, longitude, Instant.now(), UserInterfaceType.ANDROID);
        return RestUtil.messageOkayResponse(RestMessage.LOCATION_RECORDED);
    }


    private boolean ifExists(String phoneNumber) {
        return userManagementService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
    }

    private String temporaryTokenSend(String token, String destinationNumber, boolean resending) {
        if (environment.acceptsProfiles("production")) {
            if (token != null) {
                // todo : wire up a message source for this
                final String prefix = resending ? "Grassroot code (resent): " : "Grassroot code: ";
                messagingServiceBroker.sendPrioritySMS(prefix + token, destinationNumber);
            } else {
                log.warn("Did not send verification message. No system messaging configuration found.");
            }
            return "";
        } else {
            return token;
        }
    }
}