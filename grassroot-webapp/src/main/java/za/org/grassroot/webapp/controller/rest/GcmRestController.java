package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GroupResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo on 2016/04/07.
 */

@RestController
@RequestMapping(value = "/api/gcm")
public class GcmRestController {

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GcmService gcmService;



    @RequestMapping(value = "/register/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroup(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                       @RequestParam("registration_id") String registrationId) {
        User user = userManagementService.loadOrSaveUser(phoneNumber);
        gcmService.registerUser(user,registrationId);

        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.REGISTERED_FOR_PUSH, RestStatus.SUCCESS),
                HttpStatus.CREATED);

    }




}
