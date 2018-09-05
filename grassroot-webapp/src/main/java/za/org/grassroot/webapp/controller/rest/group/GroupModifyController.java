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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinCode;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.JoinWordDTO;
import za.org.grassroot.core.dto.membership.MembershipFullDTO;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupViewPriority;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.services.exception.JoinWordsExceededException;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.exception.SoleOrganizerUnsubscribeException;
import za.org.grassroot.services.group.GroupFetchBroker;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.services.group.GroupStatsBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.file.MediaUploadResult;
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
@Api("/v2/api/group/modify") @Slf4j
@RequestMapping(value = "/v2/api/group/modify")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class GroupModifyController extends GroupBaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupModifyController.class);

    private final GroupFetchBroker groupFetchBroker;
    private final GroupImageBroker groupImageBroker;
    private final AccountBroker accountBroker;
    private final AccountFeaturesBroker accountFeaturesBroker;
    private final GroupStatsBroker groupStatsBroker;

    public GroupModifyController(JwtService jwtService, UserManagementService userManagementService, GroupFetchBroker groupFetchBroker,
                                 GroupImageBroker groupImageBroker, AccountBroker accountBroker, AccountFeaturesBroker accountFeaturesBroker, GroupStatsBroker groupStatsBroker) {
        super(jwtService, userManagementService);
        this.groupFetchBroker = groupFetchBroker;
        this.groupImageBroker = groupImageBroker;
        this.accountBroker = accountBroker;
        this.accountFeaturesBroker = accountFeaturesBroker;
        this.groupStatsBroker = groupStatsBroker;
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST)
    @ApiOperation(value = "Creates new group", notes = "Creates new group and returns it's group uid")
    public ResponseEntity<GroupRefDTO> createGroup(@RequestParam String name,
                                                   @RequestParam String description,
                                                   @RequestParam GroupPermissionTemplate permissionTemplate,
                                                   @RequestParam int reminderMinutes,
                                                   @RequestParam boolean discoverable,
                                                   @RequestParam boolean defaultAddToAccount,
                                                   @RequestParam boolean pinGroup,
                                                   HttpServletRequest request) {
        log.info("creating a group, with name {}", name);
        User user = getUserFromRequest(request);
        if (user != null) {
            HashSet<MembershipInfo> membershipInfos = new HashSet<>();
            membershipInfos.add(new MembershipInfo(user, user.getDisplayName(), BaseRoles.ROLE_GROUP_ORGANIZER, null));
            Group group = groupBroker.create(user.getUid(), name, null, membershipInfos, permissionTemplate, description, reminderMinutes, true, discoverable, true);

            if (defaultAddToAccount && user.getPrimaryAccount() != null) {
                accountBroker.addGroupsToAccount(user.getPrimaryAccount().getUid(), Collections.singleton(group.getUid()), user.getUid());
            }

            if (pinGroup) {
                groupBroker.updateViewPriority(user.getUid(), group.getUid(), GroupViewPriority.PINNED);
            }

            return new ResponseEntity<>(new GroupRefDTO(group.getUid(), group.getGroupName(), group.getMemberships().size()), HttpStatus.OK);
        } else
            return new ResponseEntity<>((GroupRefDTO) null, HttpStatus.UNAUTHORIZED);
    }

    @RequestMapping(value = "/topics/set/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Set topics for a group", notes = "To avoid possible confusion and duplication, always pass " +
            "the full set of topics that should be on the group (i.e., this is a set method, not an add method)")
    public ResponseEntity<Map<String, Integer>> addTopicsToGroup(HttpServletRequest request, @PathVariable String groupUid,
                                           @RequestParam Set<String> topics) {
        groupBroker.updateTopics(getUserIdFromRequest(request), groupUid, topics);
        Map<String, Integer> topicStats = groupStatsBroker.getTopicInterestStatsRaw(groupUid, true);
        return ResponseEntity.ok(topicStats);
    }

    @RequestMapping(value = "/topics/join/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Set join topics for a group")
    public ResponseEntity setGroupJoinTopics(HttpServletRequest request, @PathVariable String groupUid,
                                             @RequestParam List<String> joinTopics) {
        log.info("okay, adding a list of join topics, as: {}", joinTopics);
        groupBroker.setJoinTopics(getUserIdFromRequest(request), groupUid, joinTopics);
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
                                                                   @RequestParam(required = false) GroupJoinMethod joinMethod,
                                                                   @RequestBody Set<MembershipInfo> membersToAdd) {
        logger.info("membersReceived = {}", membersToAdd != null ? membersToAdd.toString() : "null");
        if (membersToAdd == null) {
            throw new NoMembershipInfoException();
        }

        List<MembershipInfo> invalidMembers = findInvalidMembers(membersToAdd);
        try {
            GroupJoinMethod method = joinMethod == null ? GroupJoinMethod.ADDED_BY_OTHER_MEMBER : joinMethod;
            log.info("adding members via join method, as received: {}, passing: {}", joinMethod, method);
            groupBroker.addMembers(getUserIdFromRequest(request), groupUid, membersToAdd, method, false);
            return ResponseEntity.ok(new GroupModifiedResponse(groupBroker.load(groupUid).getName(),
                    membersToAdd.size() - invalidMembers.size(), invalidMembers));
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

    @RequestMapping(value = "members/copy/{fromGroupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Copy all the members from a group into another")
    public ResponseEntity copyAllGroupMembersToAnother(HttpServletRequest request,
                                                       @PathVariable String fromGroupUid,
                                                       @RequestParam String toGroupUid,
                                                       @RequestParam boolean keepTopics,
                                                       @RequestParam(required = false) String addTopic) {
        groupBroker.copyAllMembersIntoGroup(getUserIdFromRequest(request), fromGroupUid, toGroupUid, keepTopics, addTopic);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/members/add/taskteam/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Add member(s) to a task team / subgroup (of the 'parent' group)", notes = "Returns the modified task team / subgroup in full")
    public ResponseEntity<GroupFullDTO> addMembersToSubgroup(HttpServletRequest request,
                                                             @PathVariable String parentUid,
                                                             @RequestParam String childGroupUid,
                                                             @RequestParam Set<String> memberUids) {
        groupBroker.addMembersToSubgroup(getUserIdFromRequest(request), parentUid, childGroupUid, memberUids);
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(getUserIdFromRequest(request), parentUid, false, false, false));
    }

    @RequestMapping(value = "/members/remove/taskteam/{parentUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Add member(s) to a task team / subgroup (of the 'parent' group)", notes = "Returns the modified task team / subgroup in full")
    public ResponseEntity<GroupFullDTO> removeMembersFromSubgroup(HttpServletRequest request,
                                                                  @PathVariable String parentUid,
                                                                  @RequestParam String childGroupUid,
                                                                  @RequestParam Set<String> memberUids) {
        groupBroker.removeMembersFromSubgroup(getUserIdFromRequest(request), parentUid, childGroupUid, memberUids);
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(getUserIdFromRequest(request), parentUid, false, false, false));
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
                    GroupPermissionTemplate.CLOSED_GROUP, null, null, false, false, false);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "/deactivate/taskteam/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity<GroupFullDTO> deactivateTaskTeam(HttpServletRequest request,
                                                           @PathVariable String parentUid,
                                                           @RequestParam String taskTeamUid) {
        groupBroker.deactivateSubGroup(getUserIdFromRequest(request), parentUid, taskTeamUid);
        return ResponseEntity.ok(groupFetchBroker.fetchGroupFullInfo(getUserIdFromRequest(request), parentUid, true, true, false));
    }

    @RequestMapping(value = "/rename/taskteam/{parentUid}", method = RequestMethod.POST)
    public ResponseEntity renameTaskTeam(HttpServletRequest request,
                                         @PathVariable String parentUid,
                                         @RequestParam String taskTeamUid,
                                         @RequestParam String newName) {
        groupBroker.renameSubGroup(getUserIdFromRequest(request), parentUid, taskTeamUid, newName);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "members/add/topics/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Assign topic(s) - special tags - to a member")
    public ResponseEntity<GroupFullDTO> assignTopicsToMembers(HttpServletRequest request, @PathVariable String groupUid,
                                                              @RequestParam Set<String> memberUids,
                                                              @RequestParam List<String> topics,
                                                              @RequestParam boolean applyToAll,
                                                              @RequestParam(required = false) Boolean onlyAdd) {
        log.info("Assigning topics, applying to all ? : {}", applyToAll);
        groupBroker.assignMembershipTopics(getUserIdFromRequest(request),
                groupUid,
                applyToAll,
                memberUids,
                new HashSet<>(topics),
                onlyAdd != null && onlyAdd);
        groupStatsBroker.getTopicInterestStatsRaw(groupUid, true);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "members/remove/topics/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Remove topic(s) - special tags - from members")
    public ResponseEntity<GroupFullDTO> removeTopicsFromMembers(HttpServletRequest request, @PathVariable String groupUid,
                                                              @RequestParam List<String> memberUids,
                                                              @RequestParam List<String> topics,
                                                              @RequestParam boolean applyToAll) {
        log.info("removing topics ... apply all ? : {}", applyToAll);
        groupBroker.removeTopicFromMembers(getUserIdFromRequest(request), groupUid, topics, applyToAll, new HashSet<>(memberUids));
        groupStatsBroker.getTopicInterestStatsRaw(groupUid, true);
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

    @RequestMapping(value = "/members/modify/assignments/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Modify assignments of user, i.e., task teams, topics, affiliations", notes =
            "If calling this, pass back original values (not empty sets) if nothing has changed, else values will be cleared")
    public ResponseEntity changeMemberAssignments(HttpServletRequest request, @PathVariable String groupUid,
                                                  @RequestParam String memberUid,
                                                  @RequestParam Set<String> taskTeams,
                                                  @RequestParam Set<String> affiliations,
                                                  @RequestParam Set<String> topics) {
        log.info("altering user assignments, etc., task teams = {}, affiliations = {}, topics = {}",
                taskTeams, affiliations, topics);
        groupBroker.alterMemberTopicsTeamsOrgs(getUserIdFromRequest(request), groupUid, memberUid,
                affiliations, taskTeams, topics);
        return ResponseEntity.ok(true);
    }

    @RequestMapping(value = "/description/modify/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Change the description of a group", notes = "Only group organizer, with UPDATE_DETAILS permission, can call")
    public ResponseEntity<ResponseWrapper> changeGroupDescription(HttpServletRequest request,
                                                                  @PathVariable String groupUid,
                                                                  @RequestParam String description) {
        try {
            groupBroker.updateDescription(getUserIdFromRequest(request), groupUid, description);
            return RestUtil.errorResponse(RestMessage.GROUP_DESCRIPTION_CHANGED);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

    @RequestMapping(value = "/image/upload/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity uploadImage(HttpServletRequest request, @PathVariable String groupUid,
                                      @RequestBody MultipartFile image) {
        final String userUid = getUserIdFromRequest(request);
        log.info("Updating group, groupUid: {}, image: {}, request: {}", groupUid, image, request);
        if (image != null) {
            try {
                byte[] imageBytes = image.getBytes();
                String imageUrl = ImageUtil.generateFileName(image,request);
                groupImageBroker.saveGroupImage(userUid, groupUid, imageUrl, imageBytes);
                MediaUploadResult result = MediaUploadResult.builder().imageUrl(imageUrl).build();
                log.info("image uploaded, returning entity: {}", imageUrl);
                return ResponseEntity.ok(result);
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

    @RequestMapping(value = "/unhide/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Sets group visibility back to normal")
    public ResponseEntity<Boolean> unhideGroupForMember(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        return ResponseEntity.ok(groupBroker.updateViewPriority(userUid, groupUid, GroupViewPriority.NORMAL));
    }

    @RequestMapping(value = "/deactivate/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Deactivates a group, if less than 5 members or younger than a month", notes = "Only group creator can call")
    public ResponseEntity<Boolean> deactivateGroup(@PathVariable String groupUid, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        groupBroker.deactivate(userUid, groupUid, true);
        return ResponseEntity.ok(true);
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

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/welcome/check/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Checks for current group welcome message")
    public ResponseEntity checkForGroupWelcomeMsg(@PathVariable String groupUid) {
        Broadcast template = accountFeaturesBroker.loadWelcomeMessage(groupUid);
        return template != null ? ResponseEntity.ok(template.getSmsTemplate1()) : ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/welcome/update/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Set a message to welcome users to the group (only possible on Extra accounts")
    public ResponseEntity setGroupWelcomeMessage(@PathVariable String groupUid,
                                                 @RequestParam String message,
                                                 HttpServletRequest request) {
        accountFeaturesBroker.createGroupWelcomeMessages(getUserIdFromRequest(request), null, groupUid,
                Collections.singletonList(message), null, null, false);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ROLE_ACCOUNT_ADMIN')")
    @RequestMapping(value = "/welcome/clear/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Remove a group welcome message")
    public ResponseEntity removeGroupWelcomeMessage(@PathVariable String groupUid, HttpServletRequest request) {
        accountFeaturesBroker.deactivateGroupWelcomes(getUserIdFromRequest(request), groupUid);
        return ResponseEntity.ok().build();
    }


    @RequestMapping(value = "/member/unsubscribe",method = RequestMethod.POST)
    @ApiOperation(value = "Unsubscribe member from a group")
    public ResponseEntity<String> unsubscribeFromGroup(@RequestParam String groupUid,
                                               HttpServletRequest request){
        groupBroker.unsubscribeMember(getUserIdFromRequest(request),groupUid);
        return ResponseEntity.ok(RestMessage.MEMBER_UNSUBSCRIBED.name());
    }

    @RequestMapping(value = "/member/alias/update",method = RequestMethod.POST)
    @ApiOperation(value = "Changes the member name in the group")
    public ResponseEntity<String> updateMemberName(@RequestParam String groupUid,
                                                   @RequestParam String alias,
                                                   HttpServletRequest request){
        groupBroker.updateMemberAlias(getUserIdFromRequest(request),groupUid,alias);
        return ResponseEntity.ok(RestMessage.MEMBER_ALIAS_CHANGED.name());
    }

    private List<MembershipInfo> findInvalidMembers(Set<MembershipInfo> members) {
        return members.stream()
                .filter(m -> !m.hasValidPhoneOrEmail() || StringUtils.isEmpty(m.getDisplayName()))
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
