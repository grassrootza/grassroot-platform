package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.TokenDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.AuthenticationResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

/**
 * Created by paballo.
 */
@RestController
@RequestMapping(value = "/api/user")
public class UserRestController {


    @Autowired
    UserManagementService userManagementService;

    @Autowired
    PasswordTokenService passwordTokenService;

    @Autowired
    GroupManagementService groupManagementService;



    @RequestMapping(value = "/add/{phoneNumber}/{displayName}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> add(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("displayName") String displayName) {

        ResponseWrapper responseWrapper;
        if (!ifExists(phoneNumber)) {
            String tokenCode = userManagementService.generateAndroidUserVerifier(phoneNumber, displayName);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VERIFICATION_TOKEN_SENT, RestStatus.SUCCESS, tokenCode);
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper,
                HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/verify/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> verify(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code)
            throws Exception {

        UserDTO userDTO = new UserDTO(phoneNumber, null);
        if (passwordTokenService.isVerificationCodeValid(userDTO, code)) {
            userDTO = userManagementService.loadUserCreateRequest(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
            User user = userManagementService.createAndroidUserProfile(userDTO);
            VerificationTokenCode token = passwordTokenService.generateLongLivedCode(user);
            ResponseWrapper responseWrapper = new GenericResponseWrapper(HttpStatus.CREATED, RestMessage.USER_REGISTRATION_SUCCESSFUL,
                    RestStatus.SUCCESS, new TokenDTO(token));
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

        }
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));


    }

    @RequestMapping(value = "/login/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logon(@PathVariable("phoneNumber") String phoneNumber) {

        ResponseWrapper responseWrapper;
        if (ifExists(phoneNumber)) {
            String token = userManagementService.generateAndroidUserVerifier(phoneNumber, null);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VERIFICATION_TOKEN_SENT, RestStatus.SUCCESS,token);
            return new ResponseEntity<>(responseWrapper, HttpStatus.OK);
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.USER_DOES_NOT_EXIST, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));


    }

    @RequestMapping(value = "/login/authenticate/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> authenticate(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String token) {

        if (passwordTokenService.isVerificationCodeValid(phoneNumber, token)) {
            User user = userManagementService.loadOrSaveUser(phoneNumber);
            VerificationTokenCode longLivedToken = passwordTokenService.generateLongLivedCode(user);
            boolean hasGroups =  !groupManagementService.getActiveGroupsPartOf(user).isEmpty();
            return new ResponseEntity<>(new AuthenticationResponseWrapper(HttpStatus.OK, RestMessage.LOGIN_SUCCESS,
                    RestStatus.SUCCESS, new TokenDTO(longLivedToken),user.getDisplayName(), hasGroups), HttpStatus.OK);
        }
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN,
                RestStatus.FAILURE), HttpStatus.UNAUTHORIZED);


    }

    @RequestMapping(value = "/profile/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getProfile(@PathVariable("phoneNumber") String phoneNumber,
                                                      @PathVariable("code") String token) {

        if(passwordTokenService.isVerificationCodeValid(phoneNumber,token)){
        User user = userManagementService.loadOrSaveUser(phoneNumber);

        return new ResponseEntity(new GenericResponseWrapper(HttpStatus.OK, RestMessage.USER_PROFILE, RestStatus.SUCCESS,
                new UserDTO(user)), HttpStatus.OK);}
        {
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN, RestStatus.FAILURE),
                    HttpStatus.UNAUTHORIZED);

        }
    }

    private boolean ifExists(String phoneNumber) {
        return userManagementService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
    }


}
