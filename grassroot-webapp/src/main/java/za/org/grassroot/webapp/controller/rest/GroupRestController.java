package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Created by paballo.
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
    RoleManagementService roleManagementService;

    @Autowired
    PasswordTokenService passwordTokenService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupLogService groupLogService;


    @Autowired
    GroupBroker groupBroker;

    @Autowired
    private GroupJoinRequestService groupJoinRequestService;


    @RequestMapping(value = "create/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroup(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                       @RequestParam("groupName") String groupName, @RequestParam(value = "description", required = true) String description,
                                                       @RequestParam(value = "phoneNumbers", required = false) List<String> phoneNumbers) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
        Set<MembershipInfo> membersToAdd = Sets.newHashSet();
        membersToAdd.add(creator);

        try {
            Group group = groupBroker.create(user.getUid(), groupName, null, addMembersToGroup(phoneNumbers, membersToAdd),
                    GroupPermissionTemplate.DEFAULT_GROUP);
             groupManagementService.changeGroupDescription(group.getUid(),description,user.getUid());

        } catch (RuntimeException e) {
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, e.getMessage(), RestStatus.FAILURE),
                    HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.GROUP_CREATED, RestStatus.SUCCESS),
                HttpStatus.CREATED);

    }

    @RequestMapping(value = "list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getUserGroups(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String token) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        List<Group> groupList = groupManagementService.getActiveGroupsPartOf(user);
        ResponseWrapper responseWrapper;
        if (!groupList.isEmpty()) {
            List<GroupResponseWrapper> groups = new ArrayList<>();
            for (Group group : groupList) {
                Role role = group.getMembership(user).getRole();
                groups.add(createWrapper(group,role));
            }
            responseWrapper =  new GenericResponseWrapper(HttpStatus.OK, RestMessage.USER_GROUPS,
                    RestStatus.SUCCESS, groups);
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
        }
        responseWrapper =  new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.USER_HAS_NO_GROUPS,
                RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper,HttpStatus.valueOf(responseWrapper.getCode()));


    }

    @RequestMapping(value = "search", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> searchForGroup(@RequestParam("searchTerm") String searchTerm) {

        String tokenSearch = searchTerm.contains("*134*1994*") ?
                searchTerm.substring("*134*1994*".length(), searchTerm.length() - 1) : searchTerm;
        Group groupByToken = groupManagementService.findGroupByToken(tokenSearch);
        ResponseWrapper responseWrapper;
        if (groupByToken != null) {
            Event event = eventManagementService.getMostRecentEvent(groupByToken);
            GroupSearchWrapper groupWrapper = new GroupSearchWrapper(groupByToken, event);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.GROUP_FOUND, RestStatus.SUCCESS, groupWrapper);
        } else {
            List<Group> possibleGroups = groupBroker.findPublicGroups(searchTerm);
            List<GroupSearchWrapper> groups;
            if (!possibleGroups.isEmpty()) {
                groups = new ArrayList<>();
                for (Group group : possibleGroups) {
                    Event event = eventManagementService.getMostRecentEvent(group);
                    groups.add(new GroupSearchWrapper(group, event));
                }
                responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.POSSIBLE_GROUP_MATCHES, RestStatus.SUCCESS, groups);
            } else {
                responseWrapper = new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.NO_GROUP_MATCHING_TERM_FOUND, RestStatus.FAILURE);
            }
        }

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "join/request/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> requestToJoinGroup(@PathVariable("phoneNumber") String phoneNumber,
                                                              @PathVariable("code") String code, @RequestParam(value = "uid")
                                                              String groupToJoinUid) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        groupJoinRequestService.open(user.getUid(), groupToJoinUid);
        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_REQUEST_SENT, RestStatus.SUCCESS);

        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }



    private Set<MembershipInfo> addMembersToGroup(List<String> phoneNumbers, Set<MembershipInfo> members) {
        if (phoneNumbers != null) {
            for (String phoneNumber : phoneNumbers)
                if (PhoneNumberUtil.testInputNumber(phoneNumber)) {
                    members.add(new MembershipInfo(PhoneNumberUtil.convertPhoneNumber(phoneNumber), BaseRoles.ROLE_ORDINARY_MEMBER, null));
                }
        }
        return members;
    }

    private GroupResponseWrapper createWrapper(Group group, Role role) {
        Event event = eventManagementService.getMostRecentEvent(group);
        GroupLog groupLog = groupLogService.load(group.getId());
        GroupResponseWrapper responseWrapper;
        if (event != null) {
            if (event.getEventStartDateTime() != null && event.getEventStartDateTime()
                    .after(new Timestamp(groupLog.getCreatedDateTime().getTime()))) {
                responseWrapper = new GroupResponseWrapper(group, event, role);
            } else {
                responseWrapper = new GroupResponseWrapper(group, groupLog, role);
            }
        } else {
            responseWrapper = new GroupResponseWrapper(group, groupLog, role);
        }
        return responseWrapper;

    }


}
