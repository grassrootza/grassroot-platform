package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.*;
import za.org.grassroot.webapp.util.ImageUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

/**
 * Created by paballo.
 */
@RestController
@RequestMapping(value = "/api/group", produces = MediaType.APPLICATION_JSON_VALUE)
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

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{groupName}/{description:.+}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroup(@PathVariable String phoneNumber, @PathVariable String code,
                                                       @PathVariable String groupName, @PathVariable String description,
                                                       @RequestBody Set<MembershipInfo> membersToAdd) {

        User user = userManagementService.findByInputNumber(phoneNumber);

        Set<MembershipInfo> groupMembers = new HashSet<>(membersToAdd);
        MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
        groupMembers.add(creator);

        try {
            Group group = groupBroker.create(user.getUid(), groupName, null, groupMembers, GroupPermissionTemplate.DEFAULT_GROUP, description, null, true);
            List<GroupResponseWrapper> groupWrappers = Collections.singletonList(createGroupWrapper(group, user));
            ResponseWrapper rw = new GenericResponseWrapper(OK, RestMessage.GROUP_CREATED, RestStatus.SUCCESS, groupWrappers);
            return new ResponseEntity<>(rw, OK);
        } catch (RuntimeException e) {
            log.error("Error occurred while creating group: " + e.getMessage(), e);
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.GROUP_NOT_CREATED, RestStatus.FAILURE),
                    HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ChangedSinceData<GroupResponseWrapper>> getUserGroups(
            @PathVariable("phoneNumber") String phoneNumber,
            @PathVariable("code") String token,
            @RequestParam(name = "changedSince", required = false) Long changedSinceMillis) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);

        Instant changedSince = changedSinceMillis == null ? null : Instant.ofEpochMilli(changedSinceMillis);
        ChangedSinceData<Group> changedSinceData = groupBroker.getActiveGroups(user, changedSince);
        List<GroupResponseWrapper> groupWrappers = changedSinceData.getAddedAndUpdated().stream()
                .map(group -> createGroupWrapper(group, user))
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        ChangedSinceData<GroupResponseWrapper> response = new ChangedSinceData<>(groupWrappers, changedSinceData.getRemovedUids());
        return new ResponseEntity<>(response, OK);
    }

    @RequestMapping(value = "/get/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getGroups(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String groupUid) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, null);

        List<GroupResponseWrapper> groupWrappers = Collections.singletonList(createGroupWrapper(group, user));
        ResponseWrapper rw = new GenericResponseWrapper(OK, RestMessage.USER_GROUPS, RestStatus.SUCCESS, groupWrappers);
        return new ResponseEntity<>(rw, OK);
    }

    @RequestMapping(value = "/search/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> searchForGroup(@PathVariable String phoneNumber,
                                                          @PathVariable String code,
                                                          @RequestParam("searchTerm") String searchTerm) {

        User user = userManagementService.findByInputNumber(phoneNumber);

	    String tokenSearch = getSearchToken(searchTerm);
        Group groupByToken = groupBroker.findGroupFromJoinCode(tokenSearch);

        ResponseWrapper responseWrapper;
        if (groupByToken != null  && !groupByToken.hasMember(user)) {
            Event event = eventManagementService.getMostRecentEvent(groupByToken);
            GroupSearchWrapper groupWrapper = new GroupSearchWrapper(groupByToken, event);
            responseWrapper = new GenericResponseWrapper(OK, RestMessage.GROUP_FOUND, RestStatus.SUCCESS, groupWrapper);
        } else {
            List<Group> possibleGroups = groupBroker.findPublicGroups(searchTerm, user.getUid());
            log.info("searched for possible groups found {}, which are {}", possibleGroups.size(), possibleGroups);
	        List<GroupSearchWrapper> groups;
            if (!possibleGroups.isEmpty()) {
                groups = possibleGroups.stream()
                        .map(group -> new GroupSearchWrapper(group, eventManagementService.getMostRecentEvent(group)))
                        .collect(Collectors.toList());
                responseWrapper = new GenericResponseWrapper(OK, RestMessage.POSSIBLE_GROUP_MATCHES, RestStatus.SUCCESS, groups);
            } else {
                responseWrapper = new ResponseWrapperImpl(OK, RestMessage.NO_GROUP_MATCHING_TERM_FOUND, RestStatus.FAILURE);
            }
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/join/request/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> requestToJoinGroup(@PathVariable("phoneNumber") String phoneNumber,
                                                              @PathVariable("code") String code,
                                                              @RequestParam(value = "uid") String groupToJoinUid,
                                                              @RequestParam(value = "message") String message) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        ResponseWrapper responseWrapper;
        try {
            log.info("User " + phoneNumber + "requests to join group with uid " + groupToJoinUid);
            groupJoinRequestService.open(user.getUid(), groupToJoinUid, message);
            responseWrapper = new ResponseWrapperImpl(OK, RestMessage.GROUP_JOIN_REQUEST_SENT, RestStatus.SUCCESS);
        } catch (RequestorAlreadyPartOfGroupException e) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_PART_OF_GROUP,
                    RestStatus.FAILURE);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
    }

    @RequestMapping(value = "/join/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<List<GroupJoinRequestDTO>> listPendingJoinRequests(@PathVariable String phoneNumber,
                                                                             @PathVariable String code) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        List<GroupJoinRequest> openRequests = new ArrayList<>(groupJoinRequestService.getOpenRequestsForUser(user.getUid()));
        List<GroupJoinRequestDTO> requestDTOs = openRequests.stream()
                .map(GroupJoinRequestDTO::new)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        return new ResponseEntity<>(requestDTOs, OK);
    }

    @RequestMapping(value = "/join/respond/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> respondToGroupRequest(@PathVariable String phoneNumber,
                                                                 @PathVariable String code,
                                                                 @RequestParam String requestUid,
                                                                 @RequestParam String response) {
        try {
            log.info("Responding to request, with response = {} and request UID = {}", response, requestUid);
            User user = userManagementService.findByInputNumber(phoneNumber);
            if (response.equals("APPROVE")) {
                groupJoinRequestService.approve(user.getUid(), requestUid);
                return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_RESPONSE_PROCESSED, RestStatus.SUCCESS), OK);
            } else if (response.equals("DENY")) {
                groupJoinRequestService.decline(user.getUid(), requestUid);
                return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_RESPONSE_PROCESSED, RestStatus.SUCCESS), OK);
            } else {
                return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.CLIENT_ERROR, RestStatus.FAILURE), OK);
            }
        } catch (AccessDeniedException e) {
            // since role / permissions / assignment may have changed since request was opened ...
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.APPROVER_PERMISSIONS_CHANGED, RestStatus.FAILURE), OK);
        }
    }

    @RequestMapping(value = "/members/list/{phoneNumber}/{code}/{groupUid}/{selected}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getGroupMember(@PathVariable("phoneNumber") String phoneNumber,
                                                          @PathVariable("code") String code, @PathVariable("groupUid") String groupUid,
                                                          @PathVariable("selected") boolean selectedByDefault,
                                                          @RequestParam(value = "page", required = false) Integer requestPage,
                                                          @RequestParam(value = "size", required = false) Integer requestPageSize) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Group group = groupBroker.load(groupUid);

        // todo: really need a utility method like "return request failure" or something similar
        if (!permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            return new ResponseEntity<>(
                    new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.GROUP_ACTIVITIES, RestStatus.FAILURE), HttpStatus.BAD_REQUEST);
        }

        int page = (requestPage != null) ? requestPage : 0;
        int size = (requestPageSize != null) ? requestPageSize : groupMemberListPageSizeDefault;
        Page<User> pageable = userManagementService.getGroupMembers(group, page, size);
        ResponseWrapper responseWrapper;
        if (page > pageable.getTotalPages()) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.GROUP_ACTIVITIES, RestStatus.FAILURE);
        } else {
            List<MembershipResponseWrapper> members = pageable.getContent().stream()
                    .map(u -> new MembershipResponseWrapper(group, u, group.getMembership(u).getRole(), selectedByDefault))
                    .collect(Collectors.toList());
            responseWrapper = new GenericResponseWrapper(OK, RestMessage.GROUP_MEMBERS, RestStatus.SUCCESS, members);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
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
            groupBroker.addMembers(user.getUid(), group.getUid(), membersToAdd, false);
        }

        Group updatedGroup = groupBroker.load(groupUid);
        List<GroupResponseWrapper> groupWrapper = Collections.singletonList(createGroupWrapper(updatedGroup, user));
        ResponseWrapper rw = new GenericResponseWrapper(OK, RestMessage.MEMBERS_ADDED, RestStatus.SUCCESS, groupWrapper);

        return new ResponseEntity<>(rw, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/members/remove/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> removeMembers(@PathVariable String phoneNumber, @PathVariable String code,
                                                         @PathVariable String groupUid, @RequestParam("memberUids") Set<String> memberUids) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            groupBroker.removeMembers(user.getUid(), groupUid, memberUids);
            Group updatedGroup = groupBroker.load(groupUid);
            return new ResponseEntity<>(new GenericResponseWrapper(OK, RestMessage.MEMBERS_REMOVED, RestStatus.SUCCESS, createGroupWrapper(updatedGroup, user)), OK);
        } catch (AccessDeniedException e) {
            return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.FORBIDDEN, RestMessage.PERMISSION_DENIED, RestStatus.FAILURE), HttpStatus.FORBIDDEN);
        }
    }


    @RequestMapping(value = "/image/upload/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadImage(@PathVariable String phoneNumber, @PathVariable String code, @PathVariable String groupUid,
                                         @RequestParam("image") MultipartFile file, HttpServletRequest request) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        ResponseEntity<ResponseWrapper> responseEntity;
        if (file != null) {
            try {
                byte[] image = file.getBytes();
                String fileName = ImageUtil.generateFileName(file,request);
                groupBroker.saveGroupImage(user.getUid(), group.getUid(), fileName, image);
                responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.UPLOADED, RestStatus.SUCCESS), OK);

            } catch (IOException | IllegalArgumentException e) {
                log.info("error "+e.getLocalizedMessage());
                responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.NOT_ACCEPTABLE, RestMessage.INVALID_INPUT,
                        RestStatus.FAILURE), HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.NOT_ACCEPTABLE, RestMessage.INVALID_INPUT,
                    RestStatus.FAILURE), HttpStatus.NOT_ACCEPTABLE);
        }

        return responseEntity;

    }

    @RequestMapping(value = "/image/remove/{phoneNumber}/{code}/{groupUid}",method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> removeGroupImage(@PathVariable String phoneNumber, @PathVariable String code, @PathVariable String groupUid){

        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        ResponseEntity<ResponseWrapper> responseEntity;
        if(group.getImageUrl() !=null){
            groupBroker.removeGroupImage(user.getUid(),groupUid);
            responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.PICTURE_REMOVED, RestStatus.SUCCESS),OK);
        }else{
            responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.PICTURE_NOT_FOUND,
                    RestStatus.SUCCESS),BAD_REQUEST);
        }
        return responseEntity;

    }

    private GroupResponseWrapper createGroupWrapper(Group group, User caller) {
        Role role = group.getMembership(caller).getRole();
        Event event = eventManagementService.getMostRecentEvent(group);
        GroupLog groupLog = groupBroker.getMostRecentLog(group);

        boolean hasTask = event != null;
        GroupResponseWrapper responseWrapper;
        if (hasTask && event.getEventStartDateTime().isAfter(groupLog.getCreatedDateTime())) {
            responseWrapper = new GroupResponseWrapper(group, event, role, hasTask);
        } else {
            responseWrapper = new GroupResponseWrapper(group, groupLog, role, hasTask);
        }
        log.info("created response wrapper = {}", responseWrapper);
        return responseWrapper;

    }

    private String getSearchToken(String searchTerm) {
        return searchTerm.contains("*134*1994*") ?
                searchTerm.substring("*134*1994*".length(), searchTerm.length() - 1) : searchTerm;
    }


}
