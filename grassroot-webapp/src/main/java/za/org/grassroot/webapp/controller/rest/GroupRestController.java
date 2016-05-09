package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by paballo.
 */
@RestController
@RequestMapping(value = "/api/group")
public class GroupRestController {

    private Logger log = LoggerFactory.getLogger(GroupRestController.class);
    private static final int groupMemberListPageSizeDefault = 20;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    RoleManagementService roleManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupBroker groupBroker;

    @Autowired
    PermissionBroker permissionBroker;

    @Autowired
    private GroupJoinRequestService groupJoinRequestService;


    @RequestMapping(value = "/create/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroup(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                       @RequestParam("groupName") String groupName, @RequestParam(value = "description", required = true) String description,
                                                       @RequestParam(value = "phoneNumbers", required = false) List<String> phoneNumbers) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
        Set<MembershipInfo> membersToAdd = Sets.newHashSet();
        membersToAdd.add(creator);
        log.info("Requesting to create group with name={}", groupName);
        log.info("description ={}", description);
        try {
            groupBroker.create(user.getUid(), groupName, null, addMembersToGroup(phoneNumbers, membersToAdd),
                               GroupPermissionTemplate.DEFAULT_GROUP, description, null);

        } catch (RuntimeException e) {
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.GROUP_NOT_CREATED, RestStatus.FAILURE),
                    HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.CREATED, RestMessage.GROUP_CREATED, RestStatus.SUCCESS),
                HttpStatus.CREATED);

    }

    @RequestMapping(value = "/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getUserGroups(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String token) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Set<Group> groupSet = permissionBroker.getActiveGroups(user, null);
        ResponseWrapper responseWrapper;
        if (!groupSet.isEmpty()) {
            List<GroupResponseWrapper> groups = new ArrayList<>();
            for (Group group : groupSet) {
                Role role = group.getMembership(user).getRole();
                groups.add(createWrapper(group,role));
            }
            Collections.sort(groups, Collections.reverseOrder()); // i.e., "descending"
            responseWrapper =  new GenericResponseWrapper(HttpStatus.OK, RestMessage.USER_GROUPS,
                    RestStatus.SUCCESS, groups);
            return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
        }
        responseWrapper =  new ResponseWrapperImpl(HttpStatus.NOT_FOUND, RestMessage.USER_HAS_NO_GROUPS,
                RestStatus.FAILURE);
        return new ResponseEntity<>(responseWrapper,HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> searchForGroup(@RequestParam("searchTerm") String searchTerm) {

        String tokenSearch = getSearchToken(searchTerm);
        Group groupByToken = groupBroker.findGroupFromJoinCode(tokenSearch);
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

    @RequestMapping(value = "/join/request/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> requestToJoinGroup(@PathVariable("phoneNumber") String phoneNumber,
                                                              @PathVariable("code") String code, @RequestParam(value = "uid")
                                                              String groupToJoinUid) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        ResponseWrapper responseWrapper;
        try {
            log.info("User " + phoneNumber +"requests to join group with uid " + groupToJoinUid );
            groupJoinRequestService.open(user.getUid(), groupToJoinUid, null);
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_REQUEST_SENT, RestStatus.SUCCESS);
        } catch (RequestorAlreadyPartOfGroupException e) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_PART_OF_GROUP,
                                                      RestStatus.FAILURE);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value="/members/list/{phoneNumber}/{code}/{groupUid}", method=RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getGroupMember(@PathVariable("phoneNumber") String phoneNumber,
                                                          @PathVariable("code") String code, @PathVariable("groupUid") String groupUid,
                                                          @RequestParam(value = "page", required = false) Integer requestPage,
                                                          @RequestParam(value = "size",required = false) Integer requestPageSize){

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Group group = groupBroker.load(groupUid);

        // todo: really need a utility method like "return request failure" or something similar
        if (!permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            return new ResponseEntity<>(
                    new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.GROUP_ACTIVITIES,RestStatus.FAILURE), HttpStatus.BAD_REQUEST);
        }

        int page = (requestPage != null) ? requestPage : 0;
        int size = (requestPageSize != null)? requestPageSize : groupMemberListPageSizeDefault;
        Page<User> pageable = userManagementService.getGroupMembers(group, page, size);
        ResponseWrapper responseWrapper;
        if(page > pageable.getTotalPages()){
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.GROUP_ACTIVITIES,RestStatus.FAILURE);
        } else {
            List<MembershipResponseWrapper> members = new ArrayList<>();
            List<User> usersFromPage = pageable.getContent();
            for (User u : usersFromPage) {
                members.add(new MembershipResponseWrapper(group, u, group.getMembership(user).getRole()));
            }
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.GROUP_MEMBERS, RestStatus.SUCCESS, members);
        }
        return new ResponseEntity<>(responseWrapper,HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/members/add/{phoneNumber}/{code}/{uid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> addMembersToGroup(@PathVariable String phoneNumber, @PathVariable String code,
                                                             @PathVariable("uid") String groupUid,
                                                             @RequestBody Set<MembershipInfo> membersToAdd) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        log.info("membersReceived = {}", membersToAdd != null ? membersToAdd.toString() : "null");

        // todo : handle error
        if (membersToAdd != null && !membersToAdd.isEmpty()) {
            groupBroker.addMembers(user.getUid(), group.getUid(), membersToAdd);
        }

        return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.MEMBERS_ADDED, RestStatus.SUCCESS),
                                    HttpStatus.CREATED);
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
        GroupLog groupLog = groupBroker.getMostRecentLog(group);
        GroupResponseWrapper responseWrapper;
        if (event != null) {
            if (event.getEventStartDateTime() != null && event.getEventStartDateTime()
                    .isAfter(groupLog.getCreatedDateTime())) {
                responseWrapper = new GroupResponseWrapper(group, event, role);
            } else {
                responseWrapper = new GroupResponseWrapper(group, groupLog, role);
            }
        } else {
            responseWrapper = new GroupResponseWrapper(group, groupLog, role);
        }
        return responseWrapper;

    }

    private String getSearchToken(String searchTerm){
       return searchTerm.contains("*134*1994*") ?
                searchTerm.substring("*134*1994*".length(), searchTerm.length() - 1) : searchTerm;

    }


}
