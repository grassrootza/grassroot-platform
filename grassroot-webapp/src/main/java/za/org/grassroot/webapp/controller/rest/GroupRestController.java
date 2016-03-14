package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.*;
import za.org.grassroot.webapp.model.rest.UserDTO;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    EventManagementService eventManagementService;

    @Autowired
    PermissionsManagementService permissionsManagementService;

    @Autowired
    RoleManagementService roleManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    GroupBroker groupBroker;

    @Autowired
    GroupLogRepository groupLogRepository;



    @RequestMapping(value ="create/{phoneNumber}/{code}", method =RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroup(@PathVariable("phoneNumber") String phoneNumber,@PathVariable("code") String code, @RequestBody GroupDTO groupDTO){

            User user = userManagementService.loadOrSaveUser(phoneNumber);
            MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
            Set<MembershipInfo> membersToAdd = Sets.newHashSet();
            membersToAdd.add(creator);

             try{
                 addMembersToGroup(groupDTO.getPhoneNumbers(),membersToAdd);
                 groupBroker.create(user.getUid(), groupDTO.getGroupName(), null, membersToAdd,
                         GroupPermissionTemplate.DEFAULT_GROUP);
             }
             catch(RuntimeException e){
                 return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, e.getMessage() ,RestStatus.FAILURE),
                         HttpStatus.BAD_REQUEST);
             }
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.GROUP_CREATED ,RestStatus.SUCCESS),
                    HttpStatus.CREATED);

    }

    @RequestMapping(value = "list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getUserGroups(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String token) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        List<Group> groupList = groupManagementService.getActiveGroupsPartOf(user);
        if (!groupList.isEmpty()) {
            List<GroupResponseWrapper> groups = new ArrayList<>();
            for (Group group : groupList) {
                Event event = eventManagementService.getMostRecentEvent(group);
                Role role = roleManagementService.getUserRoleInGroup(user, group);
                if (event != null) {
                    //todo implement some form of sorting
                    groups.add(new GroupResponseWrapper(group, event, role));
                } else {
                    groups.add(new GroupResponseWrapper(group, role));
                }
            }
            return new ResponseEntity<>(new GenericResponseWrapper(HttpStatus.OK, RestMessage.USER_GROUPS,
                    RestStatus.SUCCESS, groups), HttpStatus.OK);
        }
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.USER_HAS_NO_GROUPS,
                RestStatus.FAILURE), HttpStatus.NOT_FOUND);
    }




    private void addMembersToGroup(List<String> phoneNumbers, Set<MembershipInfo> members) {
        if (phoneNumbers != null) {
            for (String phoneNumber : phoneNumbers)
               if(PhoneNumberUtil.testInputNumber(phoneNumber)){
                members.add(new MembershipInfo(PhoneNumberUtil.convertPhoneNumber(phoneNumber), BaseRoles.ROLE_ORDINARY_MEMBER, null));
        }}
    }
}
