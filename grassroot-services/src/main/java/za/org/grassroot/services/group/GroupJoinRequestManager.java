package za.org.grassroot.services.group;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.association.AssociationRequestEvent;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.notification.JoinRequestNotification;
import za.org.grassroot.core.domain.notification.JoinRequestResultNotification;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.AssocRequestEventType;
import za.org.grassroot.core.enums.AssocRequestStatus;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.JoinRequestNotOpenException;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class GroupJoinRequestManager implements GroupJoinRequestService {
    private final Logger logger = LoggerFactory.getLogger(GroupJoinRequestManager.class);

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupJoinRequestRepository groupJoinRequestRepository;
    private final AssociationRequestEventRepository groupJoinRequestEventRepository;

    private final GroupBroker groupBroker;
    private final PermissionBroker permissionBroker;

    @Autowired
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private UserLogRepository userLogRepository;

    @Autowired
    public GroupJoinRequestManager(GroupRepository groupRepository,
                                   UserRepository userRepository,
                                   GroupJoinRequestRepository groupJoinRequestRepository,
                                   AssociationRequestEventRepository groupJoinRequestEventRepository,
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
    public String open(String requestorUid, String groupUid, String description) {
        User requestor = userRepository.findOneByUid(requestorUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (group.getMembers().contains(requestor))
            throw new RequestorAlreadyPartOfGroupException("The user requesting to join is already part of this group");

        GroupJoinRequest checkPending = groupJoinRequestRepository.
                findByGroupAndRequestorAndStatus(group, requestor, AssocRequestStatus.PENDING);
        if (checkPending != null)
            return checkPending.getUid();

        logger.info("Opening new group join request: requestor={}, group={}, description={}", requestor, group, description);

        Instant time = Instant.now();
        GroupJoinRequest request = new GroupJoinRequest(requestor, group, (description != null) ? description : null);
        groupJoinRequestRepository.save(request);

        String message = messageAssemblingService.createGroupJoinRequestMessage(group.getJoinApprover(), request);
        // message format allows GCM encoder to retrieve group name without round trip to DB, so keep intact
        UserLog userLog = new UserLog(group.getJoinApprover().getUid(), UserLogType.JOIN_REQUEST,
                generateLogMessage("Join request sent, user to approve", request), UserInterfaceType.UNKNOWN);
        userLogRepository.save(userLog);

        JoinRequestNotification notification = new JoinRequestNotification(group.getJoinApprover(), message, userLog);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addNotification(notification);
        logsAndNotificationsBroker.storeBundle(bundle);

        AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.OPENED, request, requestor, time);
        groupJoinRequestEventRepository.save(event);

        logger.info("Group join request opened: {}", request);
        return request.getUid();
    }

    private String generateLogMessage(final String descMessage, final GroupJoinRequest request) {
        // this does not seem great, but alternative is to create new fiels for a highly specific purpose, and
        // as in the note below, using group log for this would duplicate and / or pollute ...
        // depending on volume of use, clear option is to create new group join request type (sep to group & user)

        final String data =
                " <xgn>"
                + request.getGroup().getName()
                + "</xgn>, "
                + "<xguid>"
                + request.getGroup().getUid()
                + "</xguid>"
                + "<xruid>"
                + request.getUid()
                + "</xruid>";

        final int remainingChars = 250 - data.length();
        return descMessage.length() < remainingChars ? descMessage + data : (descMessage.substring(0, remainingChars) + data);
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
        request.setStatus(AssocRequestStatus.APPROVED);
        request.setProcessedTime(time);

        MembershipInfo membershipInfo =
                new MembershipInfo(requestingUser.getPhoneNumber(), BaseRoles.ROLE_ORDINARY_MEMBER, requestingUser.getDisplayName());
        groupBroker.addMembers(userUid, groupToJoin.getUid(), Sets.newHashSet(membershipInfo), false);

        // there is a little duplication here as the group broker also creates a group log, but the notification needs a log
        // and better to duplicate as a different kind than to have duplicate / overlapping group logs

        UserLog userLog = new UserLog(requestingUser.getUid(), UserLogType.JOIN_REQUEST_APPROVED,
                generateLogMessage("User approved to join group, uid", request), UserInterfaceType.UNKNOWN);
        final String message = messageAssemblingService.createGroupJoinResultMessage(request, true);
        JoinRequestResultNotification notification = new JoinRequestResultNotification(requestingUser, message, userLog);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle(Collections.singleton(userLog), Collections.singleton(notification));
        logsAndNotificationsBroker.storeBundle(bundle);

        AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.APPROVED, request, user, time);
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
        request.setStatus(AssocRequestStatus.DECLINED);
        Instant time = Instant.now();
        request.setProcessedTime(time);

        UserLog userLog = new UserLog(request.getRequestor().getUid(), UserLogType.JOIN_REQUEST_DENIED,
                generateLogMessage("User denied to join group", request), UserInterfaceType.UNKNOWN);
        final String message = messageAssemblingService.createGroupJoinResultMessage(request, false);
        JoinRequestResultNotification notification = new JoinRequestResultNotification(request.getRequestor(), message, userLog);
        logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(userLog), Collections.singleton(notification)));

        AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.DECLINED, request, user, time);
        groupJoinRequestEventRepository.save(event);
    }

    @Override
    @Transactional
    public void cancel(String requestorUid, String groupUid) {
        Objects.requireNonNull(requestorUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(requestorUid);
        Group group = groupBroker.load(groupUid);

        GroupJoinRequest request = groupJoinRequestRepository.findByGroupAndRequestorAndStatus(group, user, AssocRequestStatus.PENDING);

        if (request == null) {
            // e.g., if the request has already been approved but client hasn't updated
            throw new JoinRequestNotOpenException();
        }

        // for the moment, we do not log denials or notify the requester, as will generate a lot of noise, but reconsider based on feedback
        Instant time = Instant.now();
        request.setStatus(AssocRequestStatus.CANCELLED);
        request.setProcessedTime(time);

        AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.CANCELLED, request, user, time);
        groupJoinRequestEventRepository.save(event);
    }

    @Override
    @Transactional
    public void remind(String requestorUid, String groupUid) {
        Objects.requireNonNull(requestorUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(requestorUid);
        Group group = groupBroker.load(groupUid);

        GroupJoinRequest request = groupJoinRequestRepository.findByGroupAndRequestorAndStatus(group, user, AssocRequestStatus.PENDING);
        if (request == null) {
            throw new JoinRequestNotOpenException();
        }

        // in future can use join request event to remember to control how many times users can respond (if see this being abused)

        String message = messageAssemblingService.createGroupJoinReminderMessage(group.getJoinApprover(), request);
        UserLog userLog = new UserLog(group.getJoinApprover().getUid(), UserLogType.JOIN_REQUEST,
                generateLogMessage("Join request reminder sent, user to respond", request), UserInterfaceType.UNKNOWN);
        userLogRepository.save(userLog);

        JoinRequestNotification notification = new JoinRequestNotification(group.getJoinApprover(), message, userLog); // set to priority 0 if starts happening too often
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addNotification(notification);
        logsAndNotificationsBroker.storeBundle(bundle);

        AssociationRequestEvent event = new AssociationRequestEvent(AssocRequestEventType.REMINDED, request, user, Instant.now());
        groupJoinRequestEventRepository.save(event);
    }

    @Override
    public GroupJoinRequest loadRequest(String requestUid) {
        return groupJoinRequestRepository.findOneByUid(requestUid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupJoinRequest> getPendingRequestsForUser(String userUid) {
        Sort sort = new Sort(Sort.Direction.DESC, "creationTime");
        User user = userRepository.findOneByUid(userUid);
        return groupJoinRequestRepository.findByGroupJoinApproverAndStatus(user, AssocRequestStatus.PENDING, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupJoinRequest> getPendingRequestsFromUser(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        return groupJoinRequestRepository.findByRequestorAndStatus(user, AssocRequestStatus.PENDING,
                new Sort(Sort.Direction.DESC, "creationTime"));
    }

    @Override
    @Transactional
    public List<GroupJoinRequest> getOpenUserRequestsForGroupList(String requestorUid, List<Group> possibleGroups) {
        Objects.requireNonNull(requestorUid);
        Objects.requireNonNull(possibleGroups);

        User requestor = userRepository.findOneByUid(requestorUid);
        return groupJoinRequestRepository.findByRequestorAndStatusAndGroupIn(requestor, AssocRequestStatus.PENDING, possibleGroups);
    }
}
