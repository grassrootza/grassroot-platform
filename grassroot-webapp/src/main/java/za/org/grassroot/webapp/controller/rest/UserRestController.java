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
import za.org.grassroot.core.dto.TokenDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.AuthenticationResponseWrapper;
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
    private Environment environment;

    private Logger log = LoggerFactory.getLogger(UserRestController.class);

    @RequestMapping(value = "/add/{phoneNumber}/{displayName}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> add(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("displayName") String displayName) {

        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        if (!ifExists(msisdn)) {
            log.info("Creating a verifier for a new user with phoneNumber ={}", phoneNumber);
            String tokenCode = temporaryTokenSend(userManagementService.generateAndroidUserVerifier(phoneNumber, displayName), msisdn);
            return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, tokenCode);
        } else {
            log.info("Creating a verifier for user with phoneNumber ={}, user already exists.", phoneNumber);
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS);
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

        UserDTO userDTO = new UserDTO(phoneNumber, null);
        if (passwordTokenService.isVerificationCodeValid(userDTO, otpEntered)) {
            log.info("user dto and code verified, now creating user with phoneNumber={}", phoneNumber);
            userDTO = userManagementService.loadUserCreateRequest(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
            User user = userManagementService.createAndroidUserProfile(userDTO);
            VerificationTokenCode token = passwordTokenService.generateLongLivedCode(user);
            AuthenticationResponseWrapper authWrapper = new AuthenticationResponseWrapper(HttpStatus.OK, RestMessage.USER_REGISTRATION_SUCCESSFUL,
                    RestStatus.SUCCESS, new TokenDTO(token), user.getDisplayName(), user.getLanguageCode(), false);
            return new ResponseEntity<>(authWrapper, HttpStatus.OK);
        } else {
            log.info("Token verification for new user failed");
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_OTP);
        }
    }

    @RequestMapping(value = "/login/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logon(@PathVariable("phoneNumber") String phoneNumber) {
        ResponseWrapper responseWrapper;
        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        if (ifExists(msisdn)) {
            log.info("Logging in user with phoneNumber = {}", msisdn);
            // this will send the token by SMS and return an empty string if in production, or return the token if on staging
            String token = temporaryTokenSend(userManagementService.generateAndroidUserVerifier(msisdn, null), msisdn);
            return RestUtil.okayResponseWithData(RestMessage.VERIFICATION_TOKEN_SENT, token);
        } else {
            log.info("Android login: user with phoneNumber={} does not exist", phoneNumber);
            return RestUtil.errorResponse(HttpStatus.NOT_FOUND, RestMessage.USER_DOES_NOT_EXIST);
        }
    }

    @RequestMapping(value = "/login/authenticate/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> authenticate(@PathVariable("phoneNumber") String phoneNumber,
                                                        @PathVariable("code") String token) {

        if (passwordTokenService.isVerificationCodeValid(phoneNumber, token)) {
            log.info("User authentication successful for user with phoneNumber={}", phoneNumber);
            User user = userManagementService.loadOrSaveUser(phoneNumber);
            if(!user.hasAndroidProfile()){
                userManagementService.createAndroidUserProfile(new UserDTO(user));
            }
            userManagementService.setMessagingPreference(user.getUid(), UserMessagingPreference.ANDROID_APP); // todo : maybe move to gcm registration
            VerificationTokenCode longLivedToken = passwordTokenService.generateLongLivedCode(user);
            boolean hasGroups = userManagementService.isPartOfActiveGroups(user);
            AuthenticationResponseWrapper authWrapper = new AuthenticationResponseWrapper(HttpStatus.OK, RestMessage.LOGIN_SUCCESS,
                    RestStatus.SUCCESS, new TokenDTO(longLivedToken), user.getDisplayName(),user.getLanguageCode(), hasGroups);
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
                String messageResult = smsSendingService.sendSMS("Grassroot code: " + token, destinationNumber);
                log.debug("SMS Send result: {}", messageResult);
            } else {
                log.warn("Did not send verification message. No system messaging configuration found.");
            }
            return "";
        } else {
            return token;
        }
    }


}
