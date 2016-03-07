package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PasswordTokenService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.exception.UserExistsException;
import za.org.grassroot.webapp.model.rest.UserRegistrationResponseWrapper;

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

    //TODO this method should be admin group only, or removed when testing completed
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<User> list() {
        return userRepository.findAll();
    }


    @RequestMapping(value = "/add/{phoneNumber}", method = RequestMethod.GET)
    public ResponseEntity<String> add(@PathVariable("phoneNumber") String phoneNumber){
       userManagementService.generateAndroidUserVerifier(phoneNumber);
       return new ResponseEntity<>("success", HttpStatus.OK);

    }

    @RequestMapping(value = "/verify/{code}", method = RequestMethod.POST)
    public ResponseEntity<UserRegistrationResponseWrapper> verify( @PathVariable("code") String code, @RequestBody UserDTO userDto){

       log.info(code + " " + userDto.toString());
        UserRegistrationResponseWrapper responseWrapper = new UserRegistrationResponseWrapper();

        if(passwordTokenService.isVerificationCodeValid(userDto,code)){
            User user =  new User(userDto.getPhoneNumber(), userDto.getDisplayName());

            try {
                userManagementService.createAndroidUserProfile(user);

            } catch (UserExistsException e) {

                responseWrapper.setMessage(e.getMessage());
                return new ResponseEntity<>(responseWrapper,HttpStatus.CONFLICT);
            }
            responseWrapper.setMessage("success");
            responseWrapper.setVerificationTokenCode(passwordTokenService.generateLongLivedCode(user));
            return new ResponseEntity<>(responseWrapper,HttpStatus.OK);

        }else{
            responseWrapper.setMessage("Could not validate token");
            return new  ResponseEntity<>(responseWrapper,HttpStatus.UNAUTHORIZED);
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


}
