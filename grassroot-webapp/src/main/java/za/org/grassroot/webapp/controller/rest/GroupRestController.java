package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.exception.MessengerSettingNotFoundException;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.services.MessengerSettingsService;
import za.org.grassroot.services.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.JoinRequestNotOpenException;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.webapp.enums.JoinReqType;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.MessengerSettingsDTO;
import za.org.grassroot.webapp.model.rest.GroupJoinRequestDTO;
import za.org.grassroot.webapp.model.rest.PermissionDTO;
import za.org.grassroot.webapp.model.rest.wrappers.*;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by paballo.
 * todo : split this as with group broker
 */
@RestController
@RequestMapping(value = "/api/group", produces = MediaType.APPLICATION_JSON_VALUE)
public class GroupRestController extends GroupAbstractRestController {

    private static final Logger log = LoggerFactory.getLogger(GroupRestController.class);

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private GroupJoinRequestService groupJoinRequestService;

	@Autowired
	private GeoLocationBroker geoLocationBroker;

	@Autowired
	private GroupQueryBroker groupQueryBroker;

	@Autowired
	private MessengerSettingsService messengerSettingsService;

	@Autowired
	private GcmService gcmService;

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

	@RequestMapping(value = "/members/unsubscribe/{phoneNumber}/{code}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> unsubscribe(@PathVariable String phoneNumber, @PathVariable String code,
	                                                   @RequestParam String groupUid) {
		User user = userManagementService.findByInputNumber(phoneNumber);
		try {
			groupBroker.unsubscribeMember(user.getUid(), groupUid);
			return RestUtil.messageOkayResponse(RestMessage.MEMBER_UNSUBSCRIBED);
		} catch (Exception e) { // means user has already been removed
			return RestUtil.errorResponse(HttpStatus.CONFLICT, RestMessage.MEMBER_ALREADY_LEFT);
		}
	}

	@RequestMapping(value = "/edit/multi/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> combinedEdits(@PathVariable String phoneNumber, @PathVariable String code,
	                                                     @PathVariable String groupUid,
	                                                     @RequestParam(value = "name", required = false) String name,
	                                                     @RequestParam(value = "description", required = false) String description,
	                                                     @RequestParam(value = "resetImage", required = false) boolean resetToDefaultImage,
	                                                     @RequestParam(value = "dfltImageName", required = false) GroupDefaultImage defaultImage,
	                                                     @RequestParam(value = "changePublicPrivate", required = false) boolean changePublicPrivate,
	                                                     @RequestParam(value = "isPublic", required = false) boolean isPublic,
	                                                     @RequestParam(value = "closeJoinCode", required = false) boolean closeJoinCode,
	                                                     @RequestParam(value = "membersToRemove", required = false) Set<String> membersToRemove,
	                                                     @RequestParam(value = "organizersToAdd", required = false) Set<String> organizersToAdd) {

		User user = userManagementService.findByInputNumber(phoneNumber);
		try {
			groupBroker.combinedEdits(user.getUid(), groupUid, name, description, resetToDefaultImage, defaultImage, isPublic,
					closeJoinCode, membersToRemove, organizersToAdd);
			Group updatedGroup = groupBroker.load(groupUid);
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
			Set<Permission> permissionsEnabled = group.getRole(roleName).getPermissions();
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

	@RequestMapping(value= "messenger/update/{phoneNumber}/{code}/{groupUid}", method =RequestMethod.POST)
	public ResponseEntity<ResponseWrapper> updateMemberGroupChatSetting(@PathVariable String phoneNumber,
																		@PathVariable String code,
																		@PathVariable("groupUid") String groupUid,
																		@RequestParam(value = "userUid",required = false) String userUid,
																		@RequestParam("active") boolean active, @RequestParam("userInitiated") boolean userInitiated)
			throws Exception {

		User user = userManagementService.findByInputNumber(phoneNumber);
		String userSettingTobeUpdated = (userInitiated)?user.getUid():userUid;
		log.info("userInitiated " + userInitiated);
		log.info("active " + active);
		log.info("userUid"  +userUid);
        if(!userInitiated){
            Group group = groupBroker.load(groupUid);
            permissionBroker.isGroupPermissionAvailable(user,group,Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        }
		messengerSettingsService.updateActivityStatus(userSettingTobeUpdated,groupUid,active,userInitiated);
		if(userInitiated){
			String registrationId = gcmService.getGcmKey(user);
			if(active){
				gcmService.subscribeToTopic(registrationId,groupUid);
			}else{
				gcmService.unsubScribeFromTopic(registrationId,groupUid);
			}
		}
		return RestUtil.messageOkayResponse((!active) ? RestMessage.CHAT_DEACTIVATED : RestMessage.CHAT_ACTIVATED);

	}

	@RequestMapping(value ="messenger/fetch_settings/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
	public ResponseEntity<MessengerSettingsDTO> fetchMemberGroupChatSetting(@PathVariable String phoneNumber,
																	   @PathVariable String code,
																	   @PathVariable("groupUid") String groupUid, @RequestParam(value = "userUid",required = false) String userUid) throws MessengerSettingNotFoundException{

		User user = userManagementService.findByInputNumber(phoneNumber);
		MessengerSettings messengerSettings = userUid != null ? messengerSettingsService.load(userUid, groupUid)
				: messengerSettingsService.load(user.getUid(), groupUid);
		return new ResponseEntity<>(new MessengerSettingsDTO(messengerSettings),HttpStatus.OK);

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
		Set<Permission> currentPermissions = group.getRole(roleName).getPermissions();
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

	@ExceptionHandler(MessengerSettingNotFoundException.class)
	public ResponseEntity<ResponseWrapper> messageSettingNotFound(){
		return RestUtil.errorResponse(HttpStatus.NOT_FOUND, RestMessage.MESSAGE_SETTING_NOT_FOUND);
	}

}
