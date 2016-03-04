package za.org.grassroot.webapp.controller.rest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.model.rest.GroupDTO;
import za.org.grassroot.webapp.model.rest.UserDTO;

import java.sql.Timestamp;
import java.util.*;
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

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    GroupBroker groupBroker;

    @Autowired
    GroupLogRepository groupLogRepository;


    // todo: switch to group broker
    /*@RequestMapping(value = "/add/{userid}/{phonenumbers}", method = RequestMethod.POST)
    public GroupDTO add(@PathVariable("userid") Long userid,@PathVariable("phonenumbers") String phoneNumbers) {
        Set<MembershipInfo> membershipInfoSet = new HashSet<>();
        for (String phoneNumber : PhoneNumberUtil.splitPhoneNumbers(phoneNumbers).get("valid"))
            membershipInfoSet.add(new MembershipInfo(phoneNumber, null, BaseRoles.ROLE_ORDINARY_MEMBER));
        return new GroupDTO(groupManagementService.createNewGroup(userid,PhoneNumberUtil.splitPhoneNumbers(phoneNumbers).get("valid"), true));
    }*/

    @RequestMapping(value = "/add/subgroup/{userId}/{groupId}/{subGroupName}",
            method = RequestMethod.POST)
    public GroupDTO addSubGroup(@PathVariable("userId") Long userId,
                                @PathVariable("groupId") Long groupId,
                                @PathVariable("subGroupName") String subGroupName) {
        return new GroupDTO(groupManagementService.createSubGroup(userId, groupId, subGroupName));
    }



    @RequestMapping(value = "/add/usertogroup/{userId}/{groupId}",
            method = RequestMethod.POST)
    public GroupDTO addUserToGroup(@PathVariable("userId") Long userId,
                                @PathVariable("groupId") Long groupId) {
        return new GroupDTO(groupManagementService.addGroupMember(groupId, userId, userId, true));
    }
    @RequestMapping(value = "/remove/userfromgroup/{userId}/{groupId}",
            method = RequestMethod.POST)
    public GroupDTO removeUserFromGroup(@PathVariable("userId") Long userId,
                                   @PathVariable("groupId") Long groupId) {
        return new GroupDTO(groupManagementService.removeGroupMember(groupId, userRepository.findOne(userId), userRepository.findOne(userId)));
    }

    @RequestMapping(value = "/get/userjoingroup/{userId}/{groupId}",
            method = RequestMethod.POST)
    public String getUserJoinGroup(@PathVariable("userId") Long userId,
                                 @PathVariable("groupId") Long groupId) {
        Timestamp ts = groupLogRepository.getGroupJoinedDate(groupId,userId);
        log.info("getUserJoinGroup..." + ts.toLocaleString());
        return ts.toString();
    }

    @RequestMapping(value = "/list/usersingroup/{groupId}",
            method = RequestMethod.GET)
    public List<UserDTO> listUsersInGroup(@PathVariable("groupId") Long groupId) {
        List<UserDTO> list =  new ArrayList<UserDTO>();
        for (User user : groupManagementService.loadGroup(groupId).getMembers()) {
            list.add(new UserDTO(user));
        }
        return list;
    }

    @RequestMapping(value = "/list/groupandsubgroups/{groupId}",
            method = RequestMethod.GET)
    public List<Long> listGroupAndSubGroups(@PathVariable("groupId") Long groupId) {
        List<Long> list =  new ArrayList<Long>();
        for (Group group : groupRepository.findGroupAndSubGroupsById(groupId)) {
            list.add(group.getId());
        }
        return list;
    }

    @RequestMapping(value = "/list/usersingroupandsubgroups/{groupId}",
            method = RequestMethod.GET)
    public List<UserDTO> listUsersInGroupAndSubGroups(@PathVariable("groupId") Long groupId) {
        List<UserDTO> list =  new ArrayList<UserDTO>();
        for (User user : groupManagementService.getAllUsersInGroupAndSubGroups(groupId)) {
            list.add(new UserDTO(user));
        }
        return list;
    }

}
