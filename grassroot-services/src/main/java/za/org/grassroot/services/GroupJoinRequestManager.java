package za.org.grassroot.services;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupJoinRequestEventType;
import za.org.grassroot.core.enums.GroupJoinRequestStatus;
import za.org.grassroot.core.repository.GroupJoinRequestEventRepository;
import za.org.grassroot.core.repository.GroupJoinRequestRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;

import java.time.Instant;
import java.util.List;

@Component
public class GroupJoinRequestManager implements GroupJoinRequestService {
    private final Logger logger = LoggerFactory.getLogger(GroupJoinRequestManager.class);

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupJoinRequestRepository groupJoinRequestRepository;
    private final GroupJoinRequestEventRepository groupJoinRequestEventRepository;

    private final GroupBroker groupBroker;
    private final PermissionBroker permissionBroker;

    @Autowired
    public GroupJoinRequestManager(GroupRepository groupRepository,
                                   UserRepository userRepository,
                                   GroupJoinRequestRepository groupJoinRequestRepository,
                                   GroupJoinRequestEventRepository groupJoinRequestEventRepository,
                                   GroupBroker groupBroker,
                                   PermissionBroker permissionBroker) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupJoinRequestRepository = groupJoinRequestRepository;
        this.groupJoinRequestEventRepository = groupJoinRequestEventRepository;
        this.groupBroker = groupBroker;
        this.permissionBroker = permissionBroker;
    }

    @Override
    @Transactional
    public String open(String requestorUid, String groupUid) {
        User requestor = userRepository.findOneByUid(requestorUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (group.getMembers().contains(requestor))
            throw new RequestorAlreadyPartOfGroupException("The user requesting to join is already part of this group");

        GroupJoinRequest checkPending = groupJoinRequestRepository.
                findByGroupAndRequestorAndStatus(group, requestor, GroupJoinRequestStatus.PENDING);
        if (checkPending != null)
            return checkPending.getUid();

        logger.info("Opening new group join request: requestor={}, group={}", requestor, group);

        Instant time = Instant.now();
        GroupJoinRequest request = new GroupJoinRequest(requestor, group, time);
        groupJoinRequestRepository.save(request);

        GroupJoinRequestEvent event = new GroupJoinRequestEvent(GroupJoinRequestEventType.OPENED, request, requestor, time);
        groupJoinRequestEventRepository.save(event);

        logger.info("Group join request opened: {}", request);
        return request.getUid();
    }

    @Override
    @Transactional
    public void approve(String userUid, String requestUid) {

        User user = userRepository.findOneByUid(userUid);
        GroupJoinRequest request = groupJoinRequestRepository.findOneByUid(requestUid);
        User requestingUser = request.getRequestor();
        Group groupToJoin = request.getGroup();

        if (!groupToJoin.getJoinApprover().equals(user) ||
                !permissionBroker.isGroupPermissionAvailable(user, groupToJoin, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER)) {
            throw new AccessDeniedException("User does not have needed permissions for the group");
        }

        logger.info("Approving request: request={}, user={}", request, user);
        Instant time = Instant.now();
        request.setStatus(GroupJoinRequestStatus.APPROVED);
        request.setProcessedTime(time);

        MembershipInfo membershipInfo =
                new MembershipInfo(requestingUser.getPhoneNumber(), BaseRoles.ROLE_ORDINARY_MEMBER, requestingUser.getDisplayName());
        groupBroker.addMembers(userUid, groupToJoin.getUid(), Sets.newHashSet(membershipInfo));

        GroupJoinRequestEvent event = new GroupJoinRequestEvent(GroupJoinRequestEventType.APPROVED, request, user, time);
        groupJoinRequestEventRepository.save(event);
    }

    @Override
    @Transactional
    public void decline(String userUid, String requestUid) {
        User user = userRepository.findOneByUid(userUid);
        GroupJoinRequest request = groupJoinRequestRepository.findOneByUid(requestUid);

        if (!request.getGroup().getJoinApprover().equals(user))
            throw new AccessDeniedException("User is not the join approver for this group");

        logger.info("Declining request: request={}, user={}", request, user);
        request.setStatus(GroupJoinRequestStatus.DECLINED);
        Instant time = Instant.now();
        request.setProcessedTime(time);

        GroupJoinRequestEvent event = new GroupJoinRequestEvent(GroupJoinRequestEventType.DECLINED, request, user, time);
        groupJoinRequestEventRepository.save(event);
    }

    @Override
    public GroupJoinRequest loadRequest(String requestUid) {
        return groupJoinRequestRepository.findOneByUid(requestUid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupJoinRequest> getOpenRequestsForGroup(String groupUid) {
        Sort sort = new Sort(Sort.Direction.DESC, "creationTime");
        Group group = groupRepository.findOneByUid(groupUid);
        return groupJoinRequestRepository.findByGroupAndStatus(group, GroupJoinRequestStatus.PENDING, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupJoinRequest> getOpenRequestsForUser(String userUid) {
        Sort sort = new Sort(Sort.Direction.DESC, "creationTime"); // todo: probably want to do this on multiple fields
        User user = userRepository.findOneByUid(userUid);
        return groupJoinRequestRepository.findByGroupJoinApproverAndStatus(user, GroupJoinRequestStatus.PENDING, sort);
    }
}
