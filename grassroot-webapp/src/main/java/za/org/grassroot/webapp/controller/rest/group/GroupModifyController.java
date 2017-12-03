package za.org.grassroot.webapp.controller.rest.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.exception.SoleOrganizerUnsubscribeException;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController @Grassroot2RestController
@Api("/api/group/modify")
@RequestMapping(value = "/api/group/modify")
public class GroupModifyController extends GroupBaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupModifyController.class);

    public GroupModifyController(JwtService jwtService, UserManagementService userManagementService) {
        super(jwtService, userManagementService);
    }

    @RequestMapping(value = "/members/add/{userUid}/{groupUid}", method = RequestMethod.POST)
    @ApiOperation(value = "Add members to a group", notes = "Adds members to the group, takes a set (can be a singleton) " +
            "of MembershipInfo, which requires a name, a phone number, and, optionally a role (can be ROLE_ORDINARY_MEMBER)")
    public ResponseEntity<GroupModifiedResponse> addMembersToGroup(@PathVariable String userUid,
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
            groupBroker.addMembers(userUid, groupUid, memberInfos,
                    GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
            return ResponseEntity.ok(new GroupModifiedResponse(membersToAdd.size() - invalidNumbers.size(), invalidNumbers));
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        }
    }

    @RequestMapping(value = "/leave/{userUid}/{groupUid}", method = RequestMethod.GET)
    @ApiOperation(value = "Unsubscribe user from a group", notes = "Unsubscribe from a group. Note that a user cannot leave a " +
            "group where they are the only organizer")
    public ResponseEntity<ResponseWrapper> leaveGroup(@PathVariable String userUid,
                                                      @PathVariable String groupUid) {
        try {
            groupBroker.unsubscribeMember(userUid, groupUid);
            return RestUtil.messageOkayResponse(RestMessage.MEMBER_UNSUBSCRIBED);
        } catch (SoleOrganizerUnsubscribeException e) {
            return RestUtil.errorResponse(RestMessage.SOLE_ORGANIZER);
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

    private List<String> findInvalidNumbers(Set<MembershipInfo> members) {
        return members.stream().filter(m -> !m.hasValidPhoneNumber())
                .map(MembershipInfo::getPhoneNumber)
                .collect(Collectors.toList());
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
