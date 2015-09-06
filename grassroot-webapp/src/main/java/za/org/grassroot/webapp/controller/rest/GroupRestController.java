package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.webapp.model.rest.GroupDTO;

import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/5/15.
 */
@RestController
@RequestMapping(value = "/api/group")
public class GroupRestController {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    UserRepository userRepository;

    @RequestMapping(value = "/add/{userid}/{phonenumbers}", method = RequestMethod.POST)
    public GroupDTO add(@PathVariable("userid") Long userid,@PathVariable("phonenumbers") String phoneNumbers) {
        return new GroupDTO(groupManagementService.createNewGroup(userid,PhoneNumberUtil.splitPhoneNumbers(phoneNumbers, " ").get("valid")));
    }



}
