package za.org.grassroot.webapp.controller.rest.group;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.services.exception.GroupSizeLimitExceededException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@Api("/api/group/modify")
@RequestMapping(value = "/api/group/modify")
public class GroupModifyController extends GroupBaseController {

    private static final Logger logger = LoggerFactory.getLogger(GroupModifyController.class);

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
        groupBroker.addMembers(userUid, groupUid, memberInfos, false);
        return ResponseEntity.ok(new GroupModifiedResponse(membersToAdd.size() - invalidNumbers.size(), invalidNumbers));
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

    // todo : move up and distinguish it for permissions
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseWrapper> accessDenied() {
        return RestUtil.accessDeniedResponse();
    }

}
