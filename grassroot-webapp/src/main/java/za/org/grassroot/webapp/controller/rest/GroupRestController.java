package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.JoinRequestNotOpenException;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.model.rest.PermissionDTO;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.*;
import za.org.grassroot.webapp.util.ImageUtil;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Created by paballo.
 */
@RestController
@RequestMapping(value = "/api/group", produces = MediaType.APPLICATION_JSON_VALUE)
public class GroupRestController {

    private Logger log = LoggerFactory.getLogger(GroupRestController.class);
    private static final int groupMemberListPageSizeDefault = 20;

    @Autowired
    private EventManagementService eventManagementService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private GroupJoinRequestService groupJoinRequestService;

	@Autowired
	private GeoLocationBroker geoLocationBroker;

	@Autowired
	@Qualifier("messageSourceAccessor")
	protected MessageSourceAccessor messageSourceAccessor;

	private final static Set<Permission> permissionsDisplayed = Sets.newHashSet(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
			Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
			Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
			Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
			Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
			Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
			Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{groupName}/{description:.+}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroupWithDescription(@PathVariable String phoneNumber, @PathVariable String code,
                                                                      @PathVariable String groupName, @PathVariable String description,
                                                                      @RequestBody Set<MembershipInfo> membersToAdd) {
	    log.info("creating group with description ... name : {}", groupName);
	    return createGroup(phoneNumber, groupName, description, membersToAdd);
    }

	@RequestMapping(value = "/create/{phoneNumber}/{code}/{groupName:.+}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> createGroupWithoutDescription(@PathVariable String phoneNumber, @PathVariable String code,
	                                                                     @PathVariable String groupName, @RequestBody Set<MembershipInfo> membersToAdd) {
		log.info("creating group without description ... name : {}", groupName);
		return createGroup(phoneNumber, groupName, null, membersToAdd);
	}

	private ResponseEntity<ResponseWrapper> createGroup(final String phoneNumber, final String groupName, final String description,
	                                                    Set<MembershipInfo> membersToAdd) {
		try {
			User user = userManagementService.findByInputNumber(phoneNumber);
			Group duplicate = checkForDuplicateGroup(user.getUid(), groupName);
			RestMessage restMessage;
			List<GroupResponseWrapper> returnData;
			if (duplicate != null) {
				restMessage = RestMessage.GROUP_DUPLICATE_CREATE;
				returnData = Collections.singletonList(createGroupWrapper(duplicate, user));
			} else {
				log.info("check for numbers in this set : " + membersToAdd);
				List<String> invalidNumbers = findInvalidNumbers(membersToAdd);
				if (!membersToAdd.isEmpty() && (invalidNumbers.size() == membersToAdd.size())) {
					throw new InvalidPhoneNumberException(String.join(",", invalidNumbers));
				} else {
					MembershipInfo creator = new MembershipInfo(user.getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER, user.getDisplayName());
					membersToAdd.add(creator);
					Group created = groupBroker.create(user.getUid(), groupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP,
							description, null, true);
					restMessage = RestMessage.GROUP_CREATED;
					GroupResponseWrapper wrapper = createGroupWrapper(created, user);
					wrapper.setInvalidNumbers(invalidNumbers);
					returnData = Collections.singletonList(wrapper);
				}
			}
			return RestUtil.okayResponseWithData(restMessage, returnData);
		} catch (InvalidPhoneNumberException e) {
			return RestUtil.errorResponseWithData(RestMessage.GROUP_BAD_PHONE_NUMBER, e.getMessage());
		} catch (RuntimeException e) {
			e.printStackTrace();
			return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.GROUP_NOT_CREATED);
		}
	}

	private Group handleUpdatingDuplicate(Group duplicate, User creatingUser, Set<MembershipInfo> membersToAdd, String description) {
		Objects.requireNonNull(duplicate);
		groupBroker.addMembers(creatingUser.getUid(), duplicate.getUid(), membersToAdd, false);
		if (description != null && !description.isEmpty()) {
			groupBroker.updateDescription(creatingUser.getUid(), duplicate.getUid(), description);
		}
		duplicate = groupBroker.load(duplicate.getUid());
		return duplicate;
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

	    log.info("responding ... group with removed UIDs = " + changedSinceData.getRemovedUids());

        ChangedSinceData<GroupResponseWrapper> response = new ChangedSinceData<>(groupWrappers, changedSinceData.getRemovedUids());
        return new ResponseEntity<>(response, HttpStatus.OK);
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

    @RequestMapping(value = "/search/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> searchForGroup(@PathVariable String phoneNumber,
                                                          @PathVariable String code,
                                                          @RequestParam("searchTerm") String searchTerm,
                                                          @RequestParam(value = "onlySearchNames", required = false) boolean onlySearchNames,
                                                          @RequestParam(value = "searchByLocation", required = false) boolean searchByLocation,
                                                          @RequestParam(value = "searchRadius", required = false) Integer searchRadius) {

        User user = userManagementService.findByInputNumber(phoneNumber);

	    String tokenSearch = getSearchToken(searchTerm);
        Group groupByToken = groupBroker.findGroupFromJoinCode(tokenSearch);

        ResponseEntity<ResponseWrapper> responseEntity;
        if (groupByToken != null  && !groupByToken.hasMember(user)) {
            responseEntity = RestUtil.okayResponseWithData(RestMessage.GROUP_FOUND, new GroupSearchWrapper(groupByToken));
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
	        List<Group> groupsToReturn = groupBroker.findPublicGroups(user.getUid(), searchTerm, filter, onlySearchNames);
	        if (groupsToReturn == null || groupsToReturn.isEmpty()) {
		        log.info("found no groups ... returning empty ...");
		        responseEntity = RestUtil.okayResponseWithData(RestMessage.NO_GROUP_MATCHING_TERM_FOUND, Collections.emptyList());
	        } else {
		        // next line is a slightly heavy duty way to handle separating task & name queries, vs a quick string comparison on all
		        // groups, but (a) ensures no discrepancies in what user sees, and (b) sets up for non-English/case languages
		        List<Group> possibleGroupsOnlyName = onlySearchNames ? null : groupBroker.findPublicGroups(user.getUid(), searchTerm, null, true);

		        // note : we likely want to switch this to just getting the groups, via a proper JPQL query (first optimization, then maybe above)
		        List<GroupJoinRequest> openRequests = groupJoinRequestService.getOpenUserRequestsForGroupList(user.getUid(), groupsToReturn);
		        // similarly, this should likely be incorporated into the return entity from the broker above, hence refactor once past next version
		        List<Group> groupsWithLocation = geoLocationBroker.fetchGroupsWithRecordedLocationsFromSet(new HashSet<>(groupsToReturn));

		        log.info("searched for possible groups found {}, which are {}, of which {} have locations", groupsToReturn.size(), groupsToReturn,
				        groupsWithLocation != null ? groupsWithLocation.size() : "null");

		        List<GroupSearchWrapper> groupSearchWrappers = groupsToReturn
				        .stream()
				        .map(group -> new GroupSearchWrapper(group, onlySearchNames || possibleGroupsOnlyName.contains(group),
						        groupsWithLocation.contains(group), openRequests))
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
                                                              @RequestParam(value = "uid") String groupToJoinUid,
                                                              @RequestParam(value = "message") String message) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        ResponseWrapper responseWrapper;
        try {
            log.info("User " + phoneNumber + "requests to join group with uid " + groupToJoinUid);
            groupJoinRequestService.open(user.getUid(), groupToJoinUid, message);
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.GROUP_JOIN_REQUEST_SENT, RestStatus.SUCCESS);
        } catch (RequestorAlreadyPartOfGroupException e) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_PART_OF_GROUP,
                    RestStatus.FAILURE);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));
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
                                                                             @PathVariable String code) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        List<GroupJoinRequest> openRequests = new ArrayList<>(groupJoinRequestService.getOpenRequestsForUser(user.getUid()));
        List<GroupJoinRequestDTO> requestDTOs = openRequests.stream()
                .map(GroupJoinRequestDTO::new)
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

    @RequestMapping(value = "/members/list/{phoneNumber}/{code}/{groupUid}/{selected}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getGroupMember(@PathVariable("phoneNumber") String phoneNumber,
                                                          @PathVariable("code") String code, @PathVariable("groupUid") String groupUid,
                                                          @PathVariable("selected") boolean selectedByDefault,
                                                          @RequestParam(value = "page", required = false) Integer requestPage,
                                                          @RequestParam(value = "size", required = false) Integer requestPageSize) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Group group = groupBroker.load(groupUid);

        if (!permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            return RestUtil.accessDeniedResponse();
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
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.GROUP_MEMBERS, RestStatus.SUCCESS, members);
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

	    try {
		    RestMessage returnMessage;
		    List<GroupResponseWrapper> groupWrapper;
		    if (membersToAdd != null && !membersToAdd.isEmpty()) {
			    List<String> invalidNumbers = findInvalidNumbers(membersToAdd);
			    if (invalidNumbers.size() == membersToAdd.size()) {
				    throw new InvalidPhoneNumberException(String.join(" ", invalidNumbers));
			    }
			    groupBroker.addMembers(user.getUid(), group.getUid(), membersToAdd, false);
			    GroupResponseWrapper updatedGroup = createGroupWrapper(groupBroker.load(groupUid), user);
			    updatedGroup.setInvalidNumbers(invalidNumbers);
			    groupWrapper = Collections.singletonList(updatedGroup);
			    returnMessage = (invalidNumbers.isEmpty()) ? RestMessage.MEMBERS_ADDED : RestMessage.GROUP_BAD_PHONE_NUMBER;
	        } else {
			    returnMessage = RestMessage.NO_MEMBERS_SENT;
			    groupWrapper = Collections.singletonList(createGroupWrapper(group, user));
	        }
		    return RestUtil.okayResponseWithData(returnMessage, groupWrapper);
        } catch (InvalidPhoneNumberException e) {
		    return RestUtil.errorResponseWithData(RestMessage.GROUP_BAD_PHONE_NUMBER, e.getMessage());
        } catch (AccessDeniedException e) {
	        return RestUtil.accessDeniedResponse();
        } catch (Exception e) {
		    return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.INVALID_INPUT);
	    }

    }

	private List<String> findInvalidNumbers(Set<MembershipInfo> members) {
		return members.stream()
				.filter(m -> !m.hasValidPhoneNumber())
				.map(MembershipInfo::getPhoneNumber)
				.collect(Collectors.toList());
	}

    @RequestMapping(value = "/members/remove/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> removeMembers(@PathVariable String phoneNumber, @PathVariable String code,
                                                         @PathVariable String groupUid, @RequestParam("memberUids") Set<String> memberUids) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            groupBroker.removeMembers(user.getUid(), groupUid, memberUids);
            Group updatedGroup = groupBroker.load(groupUid);
            return new ResponseEntity<>(new GenericResponseWrapper(HttpStatus.OK, RestMessage.MEMBERS_REMOVED,
		            RestStatus.SUCCESS, createGroupWrapper(updatedGroup, user)), HttpStatus.OK);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }


    @RequestMapping(value = "/image/upload/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadImage(@PathVariable String phoneNumber, @PathVariable String code, @PathVariable String groupUid,
                                                       @RequestParam("image") MultipartFile file, HttpServletRequest request) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // todo : extra compression / checks
        ResponseEntity<ResponseWrapper> responseEntity;
        if (file != null) {
            try {
                byte[] image = file.getBytes();
                String fileName = ImageUtil.generateFileName(file,request);
                groupBroker.saveGroupImage(user.getUid(), groupUid, fileName, image);
	            Group updatedGroup = groupBroker.load(groupUid);
                responseEntity = RestUtil.okayResponseWithData(RestMessage.UPLOADED, Collections.singletonList(createGroupWrapper(updatedGroup, user)));
            } catch (IOException | IllegalArgumentException e) {
                log.info("error "+e.getLocalizedMessage());
                responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.NOT_ACCEPTABLE, RestMessage.BAD_PICTURE_FORMAT,
                        RestStatus.FAILURE), HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            responseEntity = RestUtil.errorResponse(HttpStatus.NOT_ACCEPTABLE, RestMessage.PICTURE_NOT_RECEIVED);
        }

        return responseEntity;

    }

	@RequestMapping(value = "/image/default/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> changeGroupDefault(@PathVariable String phoneNumber, @PathVariable String code,
	                                                          @RequestParam String groupUid, @RequestParam GroupDefaultImage defaultImage) {

		User user = userManagementService.findByInputNumber(phoneNumber);
		try {
			log.info("removing any custom image, and updating default image to : {}", defaultImage);
			groupBroker.setGroupImageToDefault(user.getUid(), groupUid, defaultImage, true);
			Group updatedGroup = groupBroker.load(groupUid);
			return RestUtil.okayResponseWithData(RestMessage.UPLOADED, Collections.singletonList(createGroupWrapper(updatedGroup, user)));
		} catch (AccessDeniedException e) {
			return RestUtil.errorResponse(HttpStatus.FORBIDDEN, RestMessage.PERMISSION_DENIED);
		}
	}

	@RequestMapping(value = "/edit/multi/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> combinedEdits(@PathVariable String phoneNumber, @PathVariable String code,
	                                                     @PathVariable String groupUid,
	                                                     @RequestParam(value = "name", required = false) String name,
	                                                     @RequestParam(value = "resetImage", required = false) boolean resetToDefaultImage,
	                                                     @RequestParam(value = "dfltImageName", required = false) GroupDefaultImage defaultImage,
	                                                     @RequestParam(value = "changePublicPrivate", required = false) boolean changePublicPrivate,
	                                                     @RequestParam(value = "isPublic", required = false) boolean isPublic,
	                                                     @RequestParam(value = "closeJoinCode", required = false) boolean closeJoinCode,
	                                                     @RequestParam(value = "membersToRemove", required = false) List<String> membersToRemove,
	                                                     @RequestParam(value = "organizersToAdd", required = false) List<String> organizersToAdd) {

		User user = userManagementService.findByInputNumber(phoneNumber);
		Group group = groupBroker.load(groupUid);
		// todo : combine several of these, to preserve atomicity, and increase speed, though note this is a low-use-case (but still)
		try {
			log.info("processing a couple of edits ... for group : " + group.getName());
			if (membersToRemove != null && !membersToRemove.isEmpty()) {
				groupBroker.removeMembers(user.getUid(), groupUid, new HashSet<>(membersToRemove));
			}
			if (name != null && !name.trim().isEmpty() && !name.equals(group.getGroupName())) {
				groupBroker.updateName(user.getUid(), groupUid, name);
			}
			if (resetToDefaultImage) {
				groupBroker.setGroupImageToDefault(user.getUid(), groupUid, defaultImage, true);
			}
			if (changePublicPrivate) {
				groupBroker.updateDiscoverable(user.getUid(), groupUid, isPublic, user.getPhoneNumber());
			}
			if (closeJoinCode) {
				groupBroker.closeJoinToken(user.getUid(), groupUid);
			}
			if (organizersToAdd != null && !organizersToAdd.isEmpty()) {
				groupBroker.updateMembersToRole(user.getUid(), groupUid, new HashSet<>(organizersToAdd), BaseRoles.ROLE_GROUP_ORGANIZER);
			}
			Group updatedGroup = groupBroker.load(group.getUid());
			return RestUtil.okayResponseWithData(RestMessage.UPDATED, Collections.singletonList(createGroupWrapper(updatedGroup, user)));
		} catch (AccessDeniedException e) {
			return RestUtil.errorResponse(HttpStatus.FORBIDDEN, RestMessage.PERMISSION_DENIED);
		}
	}


	@RequestMapping(value = "/edit/rename/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> renameGroup(@PathVariable String phoneNumber, @PathVariable String code,
                                                       @RequestParam String groupUid, @RequestParam String name) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        ResponseEntity<ResponseWrapper> response;
        try {
            groupBroker.updateName(user.getUid(), groupUid, name);
            response = RestUtil.messageOkayResponse(RestMessage.GROUP_RENAMED);
        } catch (AccessDeniedException e) {
            response = RestUtil.accessDeniedResponse();
        }
        return response;
    }

	@RequestMapping(value = "/edit/description/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> changeGroupDescription(@PathVariable String phoneNumber, @PathVariable String code,
	                                                              @RequestParam String groupUid, @RequestParam String description) {
		User user = userManagementService.findByInputNumber(phoneNumber);
		try {
			groupBroker.updateDescription(user.getUid(), groupUid, description);
			return RestUtil.messageOkayResponse(RestMessage.GROUP_DESCRIPTION_CHANGED);
		} catch (AccessDeniedException e) {
			return RestUtil.accessDeniedResponse();
		}
	}

    @RequestMapping(value = "/edit/public_switch/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> switchGroupPublicPrivate(@PathVariable String phoneNumber, @PathVariable String code,
                                                                    @RequestParam String groupUid, @RequestParam boolean state) {
	    User user = userManagementService.findByInputNumber(phoneNumber);
	    ResponseEntity<ResponseWrapper> response;
	    try {
		    groupBroker.updateDiscoverable(user.getUid(), groupUid, state, user.getPhoneNumber());
		    response = RestUtil.messageOkayResponse(RestMessage.GROUP_DISCOVERABLE_UPDATED);
	    } catch (AccessDeniedException e) {
		    response = RestUtil.accessDeniedResponse();
	    }
	    return response;
    }

	@RequestMapping(value = "/edit/open_join/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> openJoinCode(@PathVariable String phoneNumber, @PathVariable String code,
	                                                    @RequestParam String groupUid) {
		User user = userManagementService.findByInputNumber(phoneNumber);
		ResponseEntity<ResponseWrapper> response;
		try {
			String token = groupBroker.openJoinToken(user.getUid(), groupUid, null);
			response = RestUtil.okayResponseWithData(RestMessage.GROUP_JOIN_CODE_OPENED, token);
		} catch (AccessDeniedException e) {
			response = RestUtil.accessDeniedResponse();
		}
		return response;
	}

	@RequestMapping(value = "/edit/close_join/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> closeJoinCode(@PathVariable String phoneNumber, @PathVariable String code,
	                                                     @RequestParam String groupUid) {
		User user = userManagementService.findByInputNumber(phoneNumber);
		ResponseEntity<ResponseWrapper> response;
		try {
			groupBroker.closeJoinToken(user.getUid(), groupUid);
			response = RestUtil.messageOkayResponse(RestMessage.GROUP_JOIN_CODE_CLOSED);
		} catch (AccessDeniedException e) {
			response = RestUtil.accessDeniedResponse();
		}
		return response;
	}

	@RequestMapping(value = "/edit/fetch_permissions/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> fetchPermissions(@PathVariable String phoneNumber, @PathVariable String code,
	                                                        @RequestParam String groupUid, @RequestParam String roleName) {

		Group group = groupBroker.load(groupUid);
		ResponseEntity<ResponseWrapper> response;
		try {
			Set<Permission> permissionsEnabled = permissionBroker.getPermissions(group, roleName);
			List<PermissionDTO> permissionsDTO = permissionsDisplayed.stream()
					.map(permission -> new PermissionDTO(permission, group, roleName, permissionsEnabled, messageSourceAccessor))
					.sorted()
					.collect(Collectors.toList());
			response = new ResponseEntity<>(new GenericResponseWrapper(HttpStatus.OK, RestMessage.PERMISSIONS_RETURNED, RestStatus.SUCCESS, permissionsDTO), HttpStatus.OK);
		} catch (AccessDeniedException e) {
			response = RestUtil.accessDeniedResponse();
		}
		return response;
	}

	@RequestMapping(value = "/edit/update_permissions/{phoneNumber}/{code}/{groupUid}/{roleName}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> updatePermissions(@PathVariable String phoneNumber, @PathVariable String code,
	                                                         @PathVariable String groupUid, @PathVariable String roleName,
	                                                         @RequestBody List<PermissionDTO> updatedPermissions) {
		User user = userManagementService.findByInputNumber(phoneNumber);
		Group group = groupBroker.load(groupUid);
		ResponseEntity<ResponseWrapper> response;

		try {
			Map<String, Set<Permission>> analyzedPerms = processUpdatedPermissions(group, roleName, updatedPermissions);
			groupBroker.updateGroupPermissionsForRole(user.getUid(), groupUid, roleName, analyzedPerms.get("ADDED"), analyzedPerms.get("REMOVED"));
			response = RestUtil.messageOkayResponse(RestMessage.PERMISSIONS_UPDATED);
		} catch (AccessDeniedException e) {
			response = RestUtil.accessDeniedResponse();
		}
		return response;
	}

	@RequestMapping(value = "/edit/change_role/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> changeMemberRole(@PathVariable String phoneNumber, @PathVariable String code,
	                                                        @RequestParam String groupUid, @RequestParam String memberUid,
	                                                        @RequestParam String roleName) {

		User user = userManagementService.findByInputNumber(phoneNumber);
		ResponseEntity<ResponseWrapper> response;
		try {
			groupBroker.updateMembershipRole(user.getUid(), groupUid, memberUid, roleName);
			response = RestUtil.messageOkayResponse(RestMessage.MEMBER_ROLE_CHANGED);
		} catch (AccessDeniedException e) {
			response = RestUtil.accessDeniedResponse();
		}
		return response;
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
        // log.info("created response wrapper = {}", responseWrapper);
        return responseWrapper;

    }

    private String getSearchToken(String searchTerm) {
        return searchTerm.contains("*134*1994*") ?
                searchTerm.substring("*134*1994*".length(), searchTerm.length() - 1) : searchTerm;
    }

	private Group checkForDuplicateGroup(final String creatingUserUid, final String groupName) {
		Objects.requireNonNull(creatingUserUid);
		Objects.requireNonNull(groupName);
		log.info("Checking for duplicate of group {}, with creating Uid {}", groupName.trim(), creatingUserUid);
		return groupBroker.checkForDuplicate(creatingUserUid, groupName.trim());
	}

	private Map<String, Set<Permission>> processUpdatedPermissions(Group group, String roleName, List<PermissionDTO> permissionDTOs) {
		Set<Permission> currentPermissions = permissionBroker.getPermissions(group, roleName);
		Set<Permission> permissionsAdded = new HashSet<>();
		Set<Permission> permissionsRemoved = new HashSet<>();
		for (PermissionDTO p : permissionDTOs) {
			if (currentPermissions.contains(p.getPermission()) && !p.isPermissionEnabled()) {
				permissionsRemoved.add(p.getPermission());
			} else if (!currentPermissions.contains(p.getPermission()) && p.isPermissionEnabled()) {
				permissionsAdded.add(p.getPermission());
			}
		}
		return ImmutableMap.of("ADDED", permissionsAdded, "REMOVED", permissionsRemoved);
	}

}
