package za.org.grassroot.webapp.controller.rest.group;

import com.google.common.collect.ImmutableMap;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.JoinWordDTO;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupViewPriority;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.services.exception.JoinWordsExceededException;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.exception.SoleOrganizerUnsubscribeException;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.PermissionDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.ImageUtil;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/api/group/modify") @Slf4j
@RequestMapping(value = "/api/group/modify")
public class GroupModifyController extends GroupBaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupModifyController.class);

    private final PermissionBroker permissionBroker;
    private final GroupFetchBroker groupFetchBroker;
    private final GroupImageBroker groupImageBroker;
    private final AccountGroupBroker accountGroupBroker;

    public GroupModifyController(JwtService jwtService, UserManagementService userManagementService, PermissionBroker permissionBroker, GroupFetchBroker groupFetchBroker, GroupImageBroker groupImageBroker, AccountGroupBroker accountGroupBroker) {
        super(jwtService, userManagementService);
        this.permissionBroker = permissionBroker;
        this.groupFetchBroker = groupFetchBroker;
        this.groupImageBroker = groupImageBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ApiOperation(value = "Creates new group", notes = "Creates new group and returns it's group uid")
    public ResponseEntity<GroupRefDTO> createGroup(@RequestParam String name,
                                                   @RequestParam String description,
                                                   @RequestParam GroupPermissionTemplate permissionTemplate,
                                                   @RequestParam int reminderMinutes,
                                                   @RequestParam boolean discoverable,
                                                   @RequestParam boolean defaultAddToAccount,
                                                   HttpServletRequest request) {
        User user = getUserFromRequest(request);
        if (user != null) {
            HashSet<MembershipInfo> membershipInfos = new HashSet<>();
            membershipInfos.add(new MembershipInfo(user, user.getDisplayName(), BaseRoles.ROLE_GROUP_ORGANIZER, null));
            Group group = groupBroker.create(user.getUid(), name, null, membershipInfos, permissionTemplate, description, reminderMinutes, true, discoverable);

            if (defaultAddToAccount && user.getPrimaryAccount() != null) {
                accountGroupBroker.addGroupToUserAccount(group.getUid(), user.getUid());
            }

            return new ResponseEntity<>(new GroupRefDTO(group.getUid(), group.getGroupName(), group.getMemberships().size()), HttpStatus.OK);
        } else
            return new ResponseEntity<>((GroupRefDTO) null, HttpStatus.UNAUTHORIZED);
    }

    @RequestMapping(value = "/topics/set/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Set topics for a group", notes = "To avoid possible confusion and duplication, always pass " +
            "the full set of topics that should be on the group (i.e., this is a set method, not an add method)")
    public ResponseEntity addTopicsToGroup(HttpServletRequest request, @PathVariable String groupUid,
                                           @RequestParam Set<String> topics) {
        groupBroker.updateTopics(getUserIdFromRequest(request), groupUid, topics);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/joincodes/list/active", method = RequestMethod.GET)
    @ApiOperation(value = "List all the active join words (to prevent duplicates")
    public ResponseEntity<Set<String>> listJoinCodes() {
        return ResponseEntity.ok(groupBroker.getUsedJoinWords());
    }

    @RequestMapping(value = "/joincodes/add/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Add a word that triggers user joining group")
    public ResponseEntity addJoinWordToGroup(HttpServletRequest request, @PathVariable String groupUid,
                                             @RequestParam String joinWord,
                                             @RequestParam(required = false) String longJoinUrl) {
        try {
            GroupJoinCode gjc = groupBroker.addJoinTag(getUserIdFromRequest(request), groupUid, joinWord, longJoinUrl);
            return ResponseEntity.ok(new JoinWordDTO(gjc.getCode(), gjc.getShortUrl()));
        } catch (IllegalArgumentException e) {
            return RestUtil.errorResponse(RestMessage.JOIN_WORD_TAKEN);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        } catch (JoinWordsExceededException e) {
            return RestUtil.errorResponse(RestMessage.JOIN_WORDS_EXHAUSTED);
        }
    }

    @RequestMapping(value = "/joincodes/remove/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Deactivate a word that triggers a user joining group")
    public ResponseEntity removeJoinWordFromGroup(HttpServletRequest request, @PathVariable String groupUid,
                                                  @RequestParam String joinWord,
                                                  @RequestParam(required = false) String userUid) {
        try {
            groupBroker.removeJoinTag(userUid == null ? getUserIdFromRequest(request) : userUid, groupUid, joinWord);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "/members/add/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Add members to a group", notes = "Adds members to the group, takes a set (can be a singleton) " +
            "of MembershipInfo, which requires a name, a phone number, and, optionally a role (can be ROLE_ORDINARY_MEMBER)")
    public ResponseEntity<GroupModifiedResponse> addMembersToGroup(HttpServletRequest request,
                                                                   @PathVariable String groupUid,
                                                                   @RequestBody Set<AddMemberInfo> membersToAdd) {
        logger.info("membersReceived = {}", membersToAdd != null ? membersToAdd.toString() : "null");
        if (membersToAdd == null) {
            throw new NoMembershipInfoException();
        }
        // workaround for the moment, need to fix and improve later
        Set<MembershipInfo> memberInfos = membersToAdd.stream()
                .map(AddMemberInfo::convertToMembershipInfo)
                .collect(Collectors.toSet());
        List<String> invalidNumbers = findInvalidNumbers(memberInfos);

        try {
            groupBroker.addMembers(getUserIdFromRequest(request), groupUid, memberInfos,
                    GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
            return ResponseEntity.ok(new GroupModifiedResponse(membersToAdd.size() - invalidNumbers.size(), invalidNumbers));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        }
    }

    @RequestMapping(value = "/members/remove/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Remove member(s) from a group", notes = "Removes members from a group, when passed set of those members' UIDs")
    public ResponseEntity removeMembersFromGroup(HttpServletRequest request, @PathVariable String groupUid,
                                                 @RequestParam List<String> memberUids) {
        groupBroker.removeMembers(getUserIdFromRequest(request), groupUid, new HashSet<>(memberUids));
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/members/add/taskteam/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Add member(s) to a task team / subgroup (of the 'parent' group)", notes = "Returns the modified task team / subgroup in full")
    public ResponseEntity<GroupFullDTO> addMembersToSubgroup(HttpServletRequest request, @PathVariable String parentUid,
                                                             @RequestParam String childGroupUid, @RequestParam Set<String> memberUids) {
        try {
            // todo make this atomic by moving permission validation into dedicated add to subgroup method
            permissionBroker.validateGroupPermission(getUserFromRequest(request), groupBroker.load(parentUid),
                    Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
            groupBroker.copyMembersIntoGroup(getUserIdFromRequest(request), childGroupUid, memberUids);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "/create/taskteam/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Create a task team / subgroup (of the 'parent' group) and add members to it",
            notes = "Returns the modified task team / subgroup in full")
    public ResponseEntity<GroupFullDTO> createTaskTeam(HttpServletRequest request,
                                                       @PathVariable String parentUid,
                                                       @RequestParam String taskTeamName,
                                                       @RequestParam Set<String> memberUids) {
        try {
            Group parentGroup = groupFetchBroker.fetchGroupByGroupUid(parentUid);
            Set<Membership> groupMemberships = parentGroup.getMemberships()
                    .stream()
                    .filter(membership -> memberUids.contains(membership.getUser().getUid())).collect(Collectors.toSet());

            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupMemberships);
            groupBroker.create(getUserIdFromRequest(request), taskTeamName, parentUid, membershipInfos,
                    GroupPermissionTemplate.DEFAULT_GROUP, null, null, false, false);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "members/add/topics/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Assign topic(s) - special tags - to a member")
    public ResponseEntity<GroupFullDTO> assignTopicsToMember(HttpServletRequest request, @PathVariable String groupUid,
                                                             @RequestParam List<String> memberUids, @RequestParam List<String> topics) {
        for (String memberUid : memberUids) {
            groupBroker.assignMembershipTopics(getUserIdFromRequest(request), groupUid, memberUid, new HashSet<>(topics));
        }
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/members/modify/role/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Modify the role of a member in the group", notes = "Requires GROUP_PERMISSION_UPDATE_GROUP_DETAILS")
    public ResponseEntity<MembershipFullDTO> changeMemberRole(HttpServletRequest request, @PathVariable String groupUid,
                                                              @RequestParam String memberUid, @RequestParam String roleName) {
        try {
            String userId = getUserIdFromRequest(request);
            groupBroker.updateMembershipRole(userId, groupUid, memberUid, roleName);
            return ResponseEntity.ok(groupFetchBroker.fetchGroupMember(userId, groupUid, memberUid));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "/members/modify/details/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Modify name, phone or email of a member", notes = "Request update details permission, and that member" +
            " being modified has not already set values themselves")
    public ResponseEntity changeMemberDetails(HttpServletRequest request, @PathVariable String groupUid,
                                                 @RequestParam String memberUid,
                                                 @RequestParam(required = false) String name,
                                                 @RequestParam(required = false) String email,
                                                 @RequestParam(required = false) String phone,
                                                 @RequestParam(required = false) Province province) {
        try {
            log.info("modifying member details in group, = {}", name);
            String userId = getUserIdFromRequest(request);
            groupBroker.updateMembershipDetails(userId, groupUid, memberUid, name, phone, email, province);
            return ResponseEntity.ok(groupFetchBroker.fetchGroupMember(userId, groupUid, memberUid));
        } catch (IllegalArgumentException e) {
            log.info("illegal argument in member modify", e);
            return RestUtil.errorResponse(RestMessage.MEMBER_ALREADY_SET);
        } catch (InvalidPhoneNumberException e) {
            log.info("invalid phone number in member modify", e);
            return RestUtil.errorResponse(RestMessage.INVALID_MSISDN);
        }
    }

    @RequestMapping(value = "/description/modify/{userUid}/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Change the description of a group", notes = "Only group organizer, with UPDATE_DETAILS permission, can call")
    public ResponseEntity<ResponseWrapper> changeGroupDescription(@PathVariable String userUid,
                                                                  @PathVariable String groupUid,
                                                                  @RequestParam String description) {
        try {
            groupBroker.updateDescription(userUid, groupUid, description);
            return RestUtil.errorResponse(RestMessage.GROUP_DESCRIPTION_CHANGED);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "/image/upload/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity uploadImage(HttpServletRequest request, @PathVariable String groupUid,
                                      @RequestBody MultipartFile image) {
        final String userUid = getUserIdFromRequest(request);
        if (image != null) {
            try {
                byte[] imageBytes = image.getBytes();
                String imageUrl = ImageUtil.generateFileName(image,request);
                groupImageBroker.saveGroupImage(userUid, groupUid, imageUrl, imageBytes);
                log.info("image uploaded, returning URL: {}", imageUrl);
                return ResponseEntity.ok(imageUrl);
            } catch (IOException | IllegalArgumentException e) {
                log.error("error uploading image", e);
                return RestUtil.errorResponse(RestMessage.BAD_PICTURE_FORMAT);
            } catch (AccessDeniedException e) {
                throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
            }
        } else {
            return RestUtil.errorResponse(RestMessage.PICTURE_NOT_RECEIVED);
        }
    }

    @RequestMapping(value = "/pin/{groupUid}")
    @ApiOperation(value = "Mark group as pinned", notes = "This only affects current user group membership, it is not group property")
    public ResponseEntity<ResponseWrapper> pinGroup(@PathVariable String groupUid, HttpServletRequest request) {
        String userId = getUserIdFromRequest(request);
        boolean actionApplyed = groupBroker.setGroupPinnedForUser(userId, groupUid, true);
        //return boolean indicating if pin action was successful
        return RestUtil.okayResponseWithData(RestMessage.GROUP_PINNED, actionApplyed);
    }

    @RequestMapping(value = "/unpin/{groupUid}")
    @ApiOperation(value = "Mark group as unpinned", notes = "This only affects current user group membership, it is not group property")
    public ResponseEntity<ResponseWrapper> unpinGroup(@PathVariable String groupUid, HttpServletRequest request) {

        String userId = getUserIdFromRequest(request);
        boolean actionApplyed = groupBroker.setGroupPinnedForUser(userId, groupUid, false);
        //return boolean indicating if unpin action was successful
        return RestUtil.okayResponseWithData(RestMessage.GROUP_UNPINNED, actionApplyed);
    }

    @RequestMapping(value = "/hide/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Sets group visibility to hidden", notes = "This only affects current user, not group-wide")
    public ResponseEntity<Boolean> hideGroupForMember(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        return ResponseEntity.ok(groupBroker.updateViewPriority(userUid, groupUid, GroupViewPriority.HIDDEN));
    }

    @RequestMapping(value = "/leave/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Unsubscribes a user from the group", notes = "User completely leaves the given group" +
            " (note, user cannot leave if they are only organizer)")
    public ResponseEntity<ResponseWrapper> leaveGroup(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        try {
            groupBroker.unsubscribeMember(userUid, groupUid);
            return RestUtil.messageOkayResponse(RestMessage.MEMBER_UNSUBSCRIBED);
        } catch (SoleOrganizerUnsubscribeException e) {
            return RestUtil.errorResponse(RestMessage.SOLE_ORGANIZER);
        }
    }

    @RequestMapping(value = "/settings/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value= "Modify setting for a group")
    public ResponseEntity<ResponseWrapper> combinedEdits(@PathVariable String groupUid,
                                                         @RequestParam(value = "name", required = false) String name,
                                                         @RequestParam(value = "description", required = false) String description,
                                                         @RequestParam(value = "resetImage", required = false) boolean resetToDefaultImage,
                                                         @RequestParam(value = "dfltImageName", required = false) GroupDefaultImage defaultImage,
                                                         @RequestParam(value = "isPublic", required = false) boolean isPublic,
                                                         @RequestParam(value = "reminderMinutes", required = false) int reminderMinutes,
                                                         @RequestParam(value = "closeJoinCode", required = false) boolean closeJoinCode,
                                                         @RequestParam(value = "membersToRemove", required = false) Set<String> membersToRemove,
                                                         @RequestParam(value = "organizersToAdd", required = false) Set<String> organizersToAdd,
                                                         HttpServletRequest request) {

        User user = getUserFromRequest(request);
        try {
            groupBroker.combinedEdits(user.getUid(), groupUid, name, description, resetToDefaultImage, defaultImage, isPublic,
                    closeJoinCode, membersToRemove, organizersToAdd, reminderMinutes);
            return RestUtil.messageOkayResponse(RestMessage.GROUP_SETTINGS_CHANGED);
        } catch (AccessDeniedException e) {
            return RestUtil.errorResponse(HttpStatus.FORBIDDEN, RestMessage.PERMISSION_DENIED);
        }
    }


    @RequestMapping(value = "/permissions/update/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Update group permissions")
    public ResponseEntity<ResponseWrapper> updatePermissions(@PathVariable String groupUid,
                                                             @RequestBody HashMap<String, List<PermissionDTO>> updatedPermissionsByRole,
                                                             HttpServletRequest request) {
        User user = getUserFromRequest(request);
        Group group = groupBroker.load(groupUid);
        ResponseEntity<ResponseWrapper> response;

        try {
            for (String roleName : updatedPermissionsByRole.keySet()) {
                Map<String, Set<Permission>> analyzedPerms = processUpdatedPermissions(group, roleName, updatedPermissionsByRole.get(roleName));
                groupBroker.updateGroupPermissionsForRole(user.getUid(), groupUid, roleName, analyzedPerms.get("ADDED"), analyzedPerms.get("REMOVED"));
            }
            response = RestUtil.messageOkayResponse(RestMessage.PERMISSIONS_UPDATED);
        } catch (AccessDeniedException e) {
            response = RestUtil.accessDeniedResponse();
        }
        return response;
    }

    private List<String> findInvalidNumbers(Set<MembershipInfo> members) {
        return members.stream().filter(m -> m.hasPhoneNumber() && !m.hasValidPhoneNumber())
                .map(MembershipInfo::getPhoneNumber)
                .collect(Collectors.toList());
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

    @ExceptionHandler(NoMembershipInfoException.class)
    public ResponseEntity<ResponseWrapper> handleNoMembershipInfo() {
        return RestUtil.errorResponse(RestMessage.EMPTY_LIST);
    }

    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ResponseWrapper> handleInvalidPhoneNumbers(InvalidPhoneNumberException e) {
        return RestUtil.errorResponseWithData(RestMessage.GROUP_BAD_PHONE_NUMBER, e.getMessage());
    }

    @ExceptionHandler(GroupSizeLimitExceededException.class)
    public ResponseEntity<ResponseWrapper> handleGroupSizeLimitExceeded() {
        return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.GROUP_SIZE_LIMIT);
    }

}
