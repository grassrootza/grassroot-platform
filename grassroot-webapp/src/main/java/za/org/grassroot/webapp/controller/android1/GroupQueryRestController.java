package za.org.grassroot.webapp.controller.android1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.exception.JoinRequestNotOpenException;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupLocationFilter;
import za.org.grassroot.webapp.enums.JoinReqType;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.model.rest.wrappers.*;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/09/29.
 */
@RestController
@RequestMapping(value = "/api/group", produces = MediaType.APPLICATION_JSON_VALUE)
public class GroupQueryRestController extends GroupAbstractRestController {

    private static final Logger log = LoggerFactory.getLogger(GroupQueryRestController.class);

    @Value("${grassroot.ussd.dialcode:'*134*1994*'}")
    private String ussdDialCode;

    private final GeoLocationBroker geoLocationBroker;
    private final GroupJoinRequestService groupJoinRequestService;

    @Autowired
    public GroupQueryRestController(GeoLocationBroker geoLocationBroker, GroupJoinRequestService groupJoinRequestService) {
        this.geoLocationBroker = geoLocationBroker;
        this.groupJoinRequestService = groupJoinRequestService;
    }

    @RequestMapping(value = "/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ChangedSinceData<GroupResponseWrapper>> getUserGroups(
            @PathVariable("phoneNumber") String phoneNumber,
            @PathVariable("code") String token,
            @RequestParam(name = "changedSince", required = false) Long changedSinceMillis) {

        User user = userManagementService.findByInputNumber(phoneNumber);

        Instant changedSince = changedSinceMillis == null ? null : Instant.ofEpochMilli(changedSinceMillis);
        ChangedSinceData<Group> changedSinceData = groupQueryBroker.getActiveGroups(user, changedSince);
        List<GroupResponseWrapper> groupWrappers = changedSinceData.getAddedAndUpdated().stream()
                .map(group -> createGroupWrapper(group, user))
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        log.info("responding ... group with removed UIDs = " + changedSinceData.getRemovedUids());

        ChangedSinceData<GroupResponseWrapper> response = new ChangedSinceData<>(groupWrappers, changedSinceData.getRemovedUids());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // dummy method for now to avoid path variable (switched to JWT)
    @RequestMapping(value = "/get/all")
    public ResponseEntity<List<GroupResponseWrapper>> getAllUserGroups(@RequestParam String userUid) {
        User user = userManagementService.load(userUid);
        List<GroupResponseWrapper> allGroups = groupQueryBroker.getActiveGroups(user, null)
                .getAddedAndUpdated()
                .stream()
                .map(g -> createGroupWrapper(g, user))
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        return new ResponseEntity<>(allGroups, HttpStatus.OK);
    }

    @RequestMapping(value = "/get/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getGroups(@PathVariable String phoneNumber, @PathVariable String code,
                                                     @PathVariable String groupUid) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, null);

        List<GroupResponseWrapper> groupWrappers = Collections.singletonList(createGroupWrapper(group, user));
        ResponseWrapper rw = new GenericResponseWrapper(HttpStatus.OK, RestMessage.USER_GROUPS, RestStatus.SUCCESS, groupWrappers);
        return new ResponseEntity<>(rw, HttpStatus.OK);
    }

    @RequestMapping(value = "/members/list/{phoneNumber}/{code}/{groupUid}")
    public ResponseEntity<ResponseWrapper> getGroupMembers(@PathVariable String phoneNumber, @PathVariable String groupUid) {
        log.info("Refreshing members for group : {}", groupUid);
        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        List<MembershipResponseWrapper> members = new ArrayList<>();
        // todo : watch this for n + 1 hibernate query performance ...
        group.getMemberships()
                .forEach(m -> members.add(new MembershipResponseWrapper(group, m.getUser(), m.getRole(), false)));

        log.info("From memberships : {}, created wrappers: {}", group.getMemberships(), members);

        return RestUtil.okayResponseWithData(RestMessage.GROUP_MEMBERS, members);
    }

    @RequestMapping(value = "/members/fetch/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> fetchSingleGroupMember(@PathVariable String phoneNumber,
                                                                  @RequestParam String groupUid,
                                                                  @RequestParam String userUid) {
        User thisUser = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        User user = userManagementService.load(userUid);

        if (!thisUser.equals(user)) {
            permissionBroker.validateGroupPermission(thisUser, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }

        Membership m = group.getMembership(user);
        return RestUtil.okayResponseWithData(RestMessage.GROUP_MEMBERS, new MembershipResponseWrapper(group, user, m.getRole(), false));
    }

    @RequestMapping(value = "/search/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> searchForGroup(@PathVariable String phoneNumber,
                                                          @PathVariable String code,
                                                          @RequestParam("searchTerm") String searchTerm,
                                                          @RequestParam(value = "onlySearchNames", required = false) boolean onlySearchNames,
                                                          @RequestParam(value = "searchByLocation", required = false) boolean searchByLocation,
                                                          @RequestParam(value = "searchRadius", required = false) Integer searchRadius) {

        User user = userManagementService.findByInputNumber(phoneNumber);

        String tokenSearch = searchTerm.contains(ussdDialCode) ? searchTerm.substring(ussdDialCode.length(), searchTerm.length() - 1) : searchTerm;
        Optional<Group> groupByToken = groupQueryBroker.findGroupFromJoinCode(tokenSearch);

        ResponseEntity<ResponseWrapper> responseEntity;
        if (groupByToken.isPresent() && !groupByToken.get().hasMember(user)) {
            responseEntity = RestUtil.okayResponseWithData(RestMessage.GROUP_FOUND, new GroupSearchWrapper(groupByToken.get()));
        } else {
            // the service beans accept null for the filter, in which case they just ignore location, hence doing it this way
            // note: excluding groups with no location, to avoid user confusion, but should change in future
            GroupLocationFilter filter = null;
            if (searchByLocation) {
                PreviousPeriodUserLocation lastUserLocation = geoLocationBroker.fetchUserLocation(user.getUid(), LocalDate.now());
                log.info("here is the user location : " + lastUserLocation);
                filter = lastUserLocation != null ? new GroupLocationFilter(lastUserLocation.getLocation(), searchRadius, false) : null;
            }

            log.info("searching for groups, with search by name only = {}, and with location filter = {}", onlySearchNames, filter);
            List<Group> groupsToReturn = groupQueryBroker.findPublicGroups(user.getUid(), searchTerm, filter, onlySearchNames);
            if (groupsToReturn == null || groupsToReturn.isEmpty()) {
                log.info("found no groups ... returning empty ...");
                responseEntity = RestUtil.okayResponseWithData(RestMessage.NO_GROUP_MATCHING_TERM_FOUND, Collections.emptyList());
            } else {
                // next line is a slightly heavy duty way to handle separating task & name queries, vs a quick string comparison on all
                // groups, but (a) ensures no discrepancies in what user sees, and (b) sets up for non-English/case languages
                List<Group> possibleGroupsOnlyName = onlySearchNames ? null : groupQueryBroker.findPublicGroups(user.getUid(), searchTerm, null, true);

                // note : we likely want to switch this to just getting the groups, via a proper JPQL query (first optimization, then maybe above)
                List<GroupJoinRequest> openRequests = groupJoinRequestService.getOpenUserRequestsForGroupList(user.getUid(), groupsToReturn);
                // similarly, this should likely be incorporated into the return entity from the broker above, hence refactor once past next version
                List<Group> groupsWithLocation = geoLocationBroker.fetchGroupsWithRecordedLocationsFromSet(new HashSet<>(groupsToReturn));

                log.info("searched for possible groups found {}, which are {}, of which {} have locations", groupsToReturn.size(), groupsToReturn,
                        groupsWithLocation != null ? groupsWithLocation.size() : "null");

                List<GroupSearchWrapper> groupSearchWrappers = groupsToReturn
                        .stream()
                        .map(group -> new GroupSearchWrapper(group, onlySearchNames || possibleGroupsOnlyName.contains(group),
                                groupsWithLocation != null && groupsWithLocation.contains(group), openRequests))
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());
                responseEntity = RestUtil.okayResponseWithData(RestMessage.POSSIBLE_GROUP_MATCHES, groupSearchWrappers);
            }
        }
        return responseEntity;
    }

    @RequestMapping(value = "/join/request/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> requestToJoinGroup(@PathVariable("phoneNumber") String phoneNumber,
                                                              @PathVariable("code") String code,
                                                              @RequestParam(value = "uid", required = false) String groupToJoinUid,
                                                              @RequestParam(value = "message") String message) {
        if (StringUtils.isEmpty(groupToJoinUid)) {
            log.warn("Missing groupUid in join request, check client");
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.REQUEST_MISSING_UID);
        } else {

            User user = userManagementService.findByInputNumber(phoneNumber);
            try {
                final String openedRequestUid = groupJoinRequestService.open(user.getUid(), groupToJoinUid, message);
                final GroupJoinRequestDTO returnedRequest = new GroupJoinRequestDTO(groupJoinRequestService.loadRequest(openedRequestUid), user);
                ResponseEntity<ResponseWrapper> response = RestUtil.okayResponseWithData(RestMessage.GROUP_JOIN_REQUEST_SENT, Collections.singletonList(returnedRequest));
                log.info("user requested to join group, response = {}", response.toString());
                return response;
            } catch (RequestorAlreadyPartOfGroupException e) {
                return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_PART_OF_GROUP);
            }
        }
    }

    @RequestMapping(value = "/join/request/cancel/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> cancelJoinRequest(@PathVariable String phoneNumber,
                                                             @PathVariable String code,
                                                             @RequestParam String groupUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            groupJoinRequestService.cancel(user.getUid(), groupUid);
            return RestUtil.messageOkayResponse(RestMessage.GROUP_JOIN_REQUEST_CANCELLED);
        } catch (JoinRequestNotOpenException e) {
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.GROUP_JOIN_REQUEST_NOT_FOUND);
        }
    }

    @RequestMapping(value = "/join/request/remind/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> remindOfJoinRequest(@PathVariable String phoneNumber,
                                                               @PathVariable String code,
                                                               @RequestParam String groupUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            groupJoinRequestService.remind(user.getUid(), groupUid);
            return RestUtil.messageOkayResponse(RestMessage.GROUP_JOIN_REQUEST_REMIND);
        } catch (JoinRequestNotOpenException e) {
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.GROUP_JOIN_REQUEST_NOT_FOUND);
        }
    }

    @RequestMapping(value = "/join/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<List<GroupJoinRequestDTO>> listPendingJoinRequests(@PathVariable String phoneNumber,
                                                                             @PathVariable String code,
                                                                             @RequestParam(required = false, value = "type") JoinReqType type) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        List<GroupJoinRequest> openRequests = new ArrayList<>();

        // could do this slightly more elegantly using an unless type construction, but privileging clarity
        if (JoinReqType.SENT_REQUEST.equals(type)) {
            openRequests.addAll(groupJoinRequestService.getPendingRequestsForUser(user.getUid()));
        } else if (JoinReqType.RECEIVED_REQUEST.equals(type)) {
            openRequests.addAll(groupJoinRequestService.getPendingRequestsFromUser(user.getUid()));
        } else if (type == null) {
            openRequests.addAll(groupJoinRequestService.getPendingRequestsForUser(user.getUid()));
            openRequests.addAll(groupJoinRequestService.getPendingRequestsFromUser(user.getUid()));
        }

        List<GroupJoinRequestDTO> requestDTOs = openRequests.stream()
                .map(req -> new GroupJoinRequestDTO(req, user))
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());

        return new ResponseEntity<>(requestDTOs, HttpStatus.OK);
    }

    @RequestMapping(value = "/join/respond/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> respondToGroupRequest(@PathVariable String phoneNumber,
                                                                 @PathVariable String code,
                                                                 @RequestParam String requestUid,
                                                                 @RequestParam String response) {
        try {
            log.info("Responding to request, with response = {} and request UID = {}", response, requestUid);
            User user = userManagementService.findByInputNumber(phoneNumber);
            if ("APPROVE".equals(response)) {
                groupJoinRequestService.approve(user.getUid(), requestUid);
                return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_RESPONSE_PROCESSED, RestStatus.SUCCESS), HttpStatus.OK);
            } else if ("DENY".equals(response)) {
                groupJoinRequestService.decline(user.getUid(), requestUid);
                return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_RESPONSE_PROCESSED, RestStatus.SUCCESS), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.CLIENT_ERROR, RestStatus.FAILURE), HttpStatus.OK);
            }
        } catch (AccessDeniedException e) {
            // since role / permissions / assignment may have changed since request was opened ...
            return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.APPROVER_PERMISSIONS_CHANGED);
        }
    }


}
