package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.model.rest.UserDTO;

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

    //TODO this method should be admin group only, or removed when testing completed
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<User> list() {
        return (List<User>) userRepository.findAll();
    }

    //todo according to standards this should return HTTP created status as well as the link to the resource
    //     this is currently only used for testing so revisit later
    @RequestMapping(value = "/add/{phonenumber}", method = RequestMethod.POST)
    public UserDTO add(@PathVariable("phonenumber") String phoneNumber) {
        return new UserDTO(userManagementService.loadOrSaveUser(phoneNumber));

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
