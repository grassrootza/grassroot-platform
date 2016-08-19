package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.enums.VerificationCodeType;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.AuthWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ProfileSettingsDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;

/**
 * Created by paballo.
 */
@RestController
@RequestMapping(value = "/api/user")
public class UserRestController {


    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private PasswordTokenService passwordTokenService;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @Autowired
    private SmsSendingService smsSendingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private Environment environment;

    private Logger log = LoggerFactory.getLogger(UserRestController.class);

    @RequestMapping(value = "/add/{phoneNumber}/{displayName}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> add(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("displayName") String displayName) {

        try {
            final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            if (!ifExists(msisdn)) {
                log.info("Creating a verifier for a new user with phoneNumber ={}", phoneNumber);
                String tokenCode = temporaryTokenSend(userManagementService.generateAndroidUserVerifier(phoneNumber, displayName), msisdn);
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
            final String tokenCode = temporaryTokenSend(userManagementService.regenerateUserVerifier(phoneNumber), msisdn); // will be empty in production
            return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, tokenCode);
        } catch (Exception e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.OTP_REQ_BEFORE_ADD);
        }
    }

    @RequestMapping(value = "/verify/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> verify(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String otpEntered)
            throws Exception {

        if (passwordTokenService.isShortLivedOtpValid(phoneNumber, otpEntered)) {
            log.info("user dto and code verified, now creating user with phoneNumber={}", phoneNumber);

            UserDTO userDTO = userManagementService.loadUserCreateRequest(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
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
                String token = temporaryTokenSend(userManagementService.generateAndroidUserVerifier(msisdn, null), msisdn);
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
            User user = userManagementService.loadOrSaveUser(phoneNumber);
            if (!user.hasAndroidProfile()) {
                userManagementService.createAndroidUserProfile(new UserDTO(user));
            }
            userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.ANDROID_APP); // todo : maybe move to gcm registration

            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);
            VerificationTokenCode longLivedToken = passwordTokenService.generateLongLivedAuthCode(user.getUid());
            boolean hasGroups = userManagementService.isPartOfActiveGroups(user);
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

    @RequestMapping(value = "/logout/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logoutUser(@PathVariable String phoneNumber, @PathVariable String code) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.SMS);
        passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.LONG_AUTH);
        return RestUtil.messageOkayResponse(RestMessage.USER_LOGGED_OUT);
    }

    @RequestMapping(value="/profile/settings/update/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateProfileSettings(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                                @RequestParam("displayName") String displayName, @RequestParam("language") String language, @RequestParam("alertPreference") String preference){

        User user = userManagementService.findByInputNumber(phoneNumber);
        log.info("Updating profile settings of user={}",phoneNumber);

        try {
            userManagementService.updateUserAndroidProfileSettings(user, displayName, language, AlertPreference.valueOf(preference));
            return RestUtil.messageOkayResponse(RestMessage.PROFILE_SETTINGS_UPDATED);
        } catch (IllegalArgumentException e){
            log.info("Invalid arguments supplied " + displayName + " "+ code + " " + preference);
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.PROFILE_SETTINGS_ERROR);
        }
    }

    @RequestMapping(value="/profile/settings/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getProfileSettings(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code){
        User user = userManagementService.findByInputNumber(phoneNumber);
        ProfileSettingsDTO profileSettingsDTO = new ProfileSettingsDTO(user.getDisplayName(), user.getLanguageCode(), user.getAlertPreference().toString());
        return RestUtil.okayResponseWithData(RestMessage.PROFILE_SETTINGS, profileSettingsDTO);
    }

    @RequestMapping(value= "/location/{phoneNumber}/{code}/{latitude}/{longitude:.+}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logUserLocation(@PathVariable String phoneNumber, @PathVariable String code,
                                                           @PathVariable double latitude, @PathVariable double longitude) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        log.info("Recording a location! With longitude = {} and lattitude = {}, from path string", longitude, latitude);
        geoLocationBroker.logUserLocation(user.getUid(), latitude, longitude, Instant.now());
        return RestUtil.messageOkayResponse(RestMessage.LOCATION_RECORDED);
    }

    private boolean ifExists(String phoneNumber) {
        return userManagementService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
    }

    private String temporaryTokenSend(String token, String destinationNumber) {
        if (environment.acceptsProfiles("production")) {
            if (token != null && System.getenv("SMSUSER") != null && System.getenv("SMSPASS") != null) {
                // todo : wire up a message source for this
                // the priority sender will default to smsuser & smspass if priority account details not found, hence leaving the check on those
                smsSendingService.sendPrioritySMS("Grassroot code: " + token, destinationNumber);
            } else {
                log.warn("Did not send verification message. No system messaging configuration found.");
            }
            return "";
        } else {
            return token;
        }
    }
}