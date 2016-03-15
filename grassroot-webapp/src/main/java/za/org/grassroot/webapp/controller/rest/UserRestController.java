package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.dto.TokenDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.SignInResponseWrapper;

import java.util.List;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/user")
public class UserRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    UserRepository userRepository;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    PasswordTokenService passwordTokenService;

    @Autowired
    GroupManagementService groupManagementService;


    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<User> list() {
        return userRepository.findAll();
    }


    @RequestMapping(value = "/add/{phoneNumber}/{displayName}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> add(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("displayName") String displayName) {

        ResponseWrapper responseWrapper;
        if (!checkIfExists(phoneNumber)) {
            String tokenCode = userManagementService.generateAndroidUserVerifier(phoneNumber, displayName);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VERIFICATION_TOKEN_SENT, RestStatus.SUCCESS, tokenCode);
            return new ResponseEntity<>(responseWrapper, HttpStatus.OK);
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper,
                HttpStatus.CONFLICT);

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
            return new ResponseEntity<>(responseWrapper, HttpStatus.CREATED);

        }
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.NOT_ACCEPTABLE);


    }

    @RequestMapping(value = "/login/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> logon(@PathVariable("phoneNumber") String phoneNumber) {

        ResponseWrapper responseWrapper;
        if (checkIfExists(phoneNumber)) {
            String token = userManagementService.generateAndroidUserVerifier(phoneNumber, null);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.VERIFICATION_TOKEN_SENT, RestStatus.SUCCESS,token);
            return new ResponseEntity<>(responseWrapper, HttpStatus.OK);
        }
        responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.USER_DOES_NOT_EXIST, RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper, HttpStatus.NOT_FOUND);


    }

    @RequestMapping(value = "/login/authenticate/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> authenticate(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String token) {

        if (passwordTokenService.isVerificationCodeValid(phoneNumber, token)) {
            User user = userManagementService.loadOrSaveUser(phoneNumber);
            VerificationTokenCode longLivedToken = passwordTokenService.generateLongLivedCode(user);
            boolean hasGroups =  groupManagementService.getActiveGroupsPartOf(user).size() >0;
            return new ResponseEntity<>(new SignInResponseWrapper(HttpStatus.OK, RestMessage.LOGIN_SUCCESS,
                    RestStatus.SUCCESS, new TokenDTO(longLivedToken),hasGroups), HttpStatus.OK);
        }
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN,
                RestStatus.FAILURE), HttpStatus.UNAUTHORIZED);


    }

    @RequestMapping(value = "/profile/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getProfile(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String token) {

        if(passwordTokenService.isVerificationCodeValid(phoneNumber,token)){
        User user = userManagementService.loadOrSaveUser(phoneNumber);

        return new ResponseEntity(new GenericResponseWrapper(HttpStatus.OK, RestMessage.USER_PROFILE, RestStatus.SUCCESS,
                new UserDTO(user)), HttpStatus.OK);}
        {
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN, RestStatus.FAILURE),
                    HttpStatus.UNAUTHORIZED);

        }
    }



    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    public UserDTO get(@PathVariable("id") Long id) {
        return new UserDTO(userRepository.findOne(id));

    }

    @RequestMapping(value = "/setinitiatedsession/{userId}", method = RequestMethod.POST)
    public UserDTO setInitiatedSession(@PathVariable("userId") Long userId) {
        return new UserDTO(userManagementService.setInitiatedSession(userManagementService.loadUser(userId)));

    }

    private boolean checkIfExists(String phoneNumber) {
        return userManagementService.userExist(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
    }


}
