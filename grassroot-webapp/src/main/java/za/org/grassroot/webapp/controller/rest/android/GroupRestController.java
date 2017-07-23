package za.org.grassroot.webapp.controller.rest.android;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import liquibase.util.StringUtils;
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
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.exception.GroupChatSettingNotFoundException;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.services.group.GroupChatBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.user.GcmRegistrationBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.GroupChatSettingsDTO;
import za.org.grassroot.webapp.model.rest.PermissionDTO;
import za.org.grassroot.webapp.model.rest.wrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.GroupResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

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

    private GroupChatBroker groupChatService;
    private MessagingServiceBroker messagingServiceBroker;
    private GcmRegistrationBroker gcmRegistrationBroker;

    private final MessageSourceAccessor messageSourceAccessor;

    private final static Set<Permission> permissionsDisplayed = Sets.newHashSet(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
            Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
            Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
            Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
            Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
            Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

    @Autowired
    public GroupRestController(@Qualifier("messageSourceAccessor") MessageSourceAccessor messageSourceAccessor) {
        this.messageSourceAccessor = messageSourceAccessor;
    }

    @Autowired(required = false)
    public void setGcmRegistrationBroker(GcmRegistrationBroker gcmRegistrationBroker) {
        this.gcmRegistrationBroker = gcmRegistrationBroker;
    }

    @Autowired(required = false)
    public void setMessagingServiceBroker(MessagingServiceBroker messagingServiceBroker) {
        this.messagingServiceBroker = messagingServiceBroker;
    }

    @Autowired(required = false)
    public void setGroupChatService(GroupChatBroker groupChatService) {
        this.groupChatService = groupChatService;
    }

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{groupName}/{description:.+}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroupWithDescription(@PathVariable String phoneNumber, @PathVariable String code,
                                                                      @PathVariable String groupName, @PathVariable String description,
                                                                      @RequestBody Set<MembershipInfo> membersToAdd) {
        return createGroup(phoneNumber, groupName, description, membersToAdd);
    }

    @RequestMapping(value = "/create/{phoneNumber}/{code}/{groupName:.+}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> createGroupWithoutDescription(@PathVariable String phoneNumber, @PathVariable String code,
                                                                         @PathVariable String groupName, @RequestBody Set<MembershipInfo> membersToAdd) {
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

    @RequestMapping(value = "members/left/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> numberMembersLeftForGroup(@PathVariable String groupUid) {
        return RestUtil.okayResponseWithData(RestMessage.GROUP_SIZE_LIMIT, groupBroker.numberMembersBeforeLimit(groupUid));
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
        } catch (GroupSizeLimitExceededException e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.GROUP_SIZE_LIMIT);
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

    @RequestMapping(value = "/edit/language/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> changeGroupLanguage(@PathVariable String phoneNumber, @RequestParam String groupUid,
                                                               @RequestParam String language) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            groupBroker.updateGroupDefaultLanguage(user.getUid(), groupUid, language, false);
            return RestUtil.messageOkayResponse(RestMessage.GROUP_LANGUAGE_CHANGED);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/alias/change/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> changeMemberAlias(@PathVariable String phoneNumber,
                                                             @RequestParam String groupUid,
                                                             @RequestParam String alias) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        if (!StringUtils.isEmpty(alias)) {
            groupBroker.updateMemberAlias(user.getUid(), groupUid, alias);
        }
        return RestUtil.messageOkayResponse(RestMessage.MEMBER_ALIAS_CHANGED);
    }

    @RequestMapping(value = "/alias/check/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> queryMemberAlias(@PathVariable String phoneNumber,
                                                            @PathVariable String groupUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        return RestUtil.okayResponseWithData(RestMessage.MEMBER_ALIAS_RETURNED,
                group.getMembership(user).getDisplayName());
    }


    /*
    Below are legacy as Group chat is removed, but retaining for old clients
     */

    @RequestMapping(value = "messenger/update/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateMemberGroupChatSetting(@PathVariable String phoneNumber,
                                                                        @PathVariable String code,
                                                                        @PathVariable("groupUid") String groupUid,
                                                                        @RequestParam(value = "userUid", required = false) String userUid,
                                                                        @RequestParam("active") boolean active, @RequestParam("userInitiated") boolean userInitiated)
            throws Exception {

        User user = userManagementService.findByInputNumber(phoneNumber);
        String userSettingTobeUpdated = (userInitiated) ? user.getUid() : userUid;
        if (!userInitiated) {
            Group group = groupBroker.load(groupUid);
            permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_MUTE_MEMBER);
        }
        messagingServiceBroker.updateActivityStatus(userSettingTobeUpdated, groupUid, active, userInitiated);
        if (userInitiated && gcmRegistrationBroker.hasGcmKey(user)) {
            gcmRegistrationBroker.changeTopicSubscription(user.getUid(), groupUid, active);
        }
        return RestUtil.messageOkayResponse((!active) ? RestMessage.CHAT_DEACTIVATED : RestMessage.CHAT_ACTIVATED);
    }

    @RequestMapping(value = "messenger/ping/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> ping(@PathVariable String phoneNumber,
                                                @PathVariable String code,
                                                @PathVariable("groupUid") String groupUid) throws GroupChatSettingNotFoundException {

        return RestUtil.messageOkayResponse(RestMessage.PING);
    }

    @RequestMapping(value = "messenger/fetch_settings/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<GroupChatSettingsDTO> fetchMemberGroupChatSetting(@PathVariable String phoneNumber,
                                                                            @PathVariable String code,
                                                                            @PathVariable("groupUid") String groupUid, @RequestParam(value = "userUid", required = false) String userUid) throws GroupChatSettingNotFoundException {

        User user = userManagementService.findByInputNumber(phoneNumber);
        GroupChatSettings groupChatSettings = userUid != null ? groupChatService.load(userUid, groupUid)
                : groupChatService.load(user.getUid(), groupUid);
        List<String> mutedUsers = groupChatService.usersMutedInGroup(groupUid);
        return new ResponseEntity<>(new GroupChatSettingsDTO(groupChatSettings, mutedUsers), HttpStatus.OK);

    }

    @RequestMapping(value = "messenger/mark_read/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> marksAsRead(@PathVariable String phoneNumber, @PathVariable String code, @PathVariable String groupUid, @RequestParam Set<String> messageUids) {
        Group group = groupBroker.load(groupUid);
        messagingServiceBroker.markMessagesAsRead(groupUid, messageUids);
        return RestUtil.messageOkayResponse(RestMessage.CHATS_MARKED_AS_READ);
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

    @ExceptionHandler(GroupChatSettingNotFoundException.class)
    public ResponseEntity<ResponseWrapper> messageSettingNotFound() {
        return RestUtil.errorResponse(HttpStatus.NOT_FOUND, RestMessage.MESSAGE_SETTING_NOT_FOUND);
    }

}
