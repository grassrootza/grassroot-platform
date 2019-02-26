package za.org.grassroot.services.group;

import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.util.StringUtils;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupLogDTO;
import za.org.grassroot.core.dto.group.GroupMembersDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.dto.group.GroupWebDTO;
import za.org.grassroot.core.dto.group.MembershipRecordDTO;
import za.org.grassroot.core.dto.membership.MembershipDTO;
import za.org.grassroot.core.dto.membership.MembershipFullDTO;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.CampaignLogRepository;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.util.FullTextSearchUtils;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.GroupSpecifications.hasParent;
import static za.org.grassroot.core.specifications.GroupSpecifications.isActive;

/**
 * Primary class for new style group feching, watch performance very closely (e.g., on size of memberships)
 */
@Service @Slf4j
public class GroupFetchBrokerImpl implements GroupFetchBroker {

    // todo: make configurable
    private static final int MAX_DTO_MEMBERS = 1000;

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupLogRepository groupLogRepository;
    private final MembershipRepository membershipRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final PermissionBroker permissionBroker;
    private final LogsAndNotificationsBroker logsBroker;

    @Autowired
    public GroupFetchBrokerImpl(UserRepository userRepository, GroupRepository groupRepository,
                                GroupLogRepository groupLogRepository, MembershipRepository membershipRepository,
                                CampaignLogRepository campaignLogRepository, LogsAndNotificationsBroker logsBroker,
                                PermissionBroker permissionBroker) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupLogRepository = groupLogRepository;
        this.membershipRepository = membershipRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.permissionBroker = permissionBroker;
        this.logsBroker = logsBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged) {
        Objects.requireNonNull(excludedGroupsByTimeChanged);

        User user = userRepository.findOneByUid(userUid);
        Specification<Group> specifications = Specification
                .where(GroupSpecifications.isActive())
                .and(GroupSpecifications.userIsMember(user));

        final Set<String> excludedGroupUids = excludedGroupsByTimeChanged.keySet();
        if (!excludedGroupsByTimeChanged.isEmpty()) {
            specifications = specifications.and(
                    Specification.not(GroupSpecifications.uidIn(excludedGroupUids)));
        }

        Set<String> newGroupUids = groupRepository.findAll(specifications)
                .stream()
                .map(Group::getUid)
                .collect(Collectors.toSet());

        Set<String> uidsToLookUp = new HashSet<>(excludedGroupUids);
        uidsToLookUp.addAll(newGroupUids);

        final Set<Group> groups = this.groupRepository.findByUidIn(uidsToLookUp);
        final List<GroupTimeChangedDTO> groupTimeChangedDTOS = groups.stream()
                .map(group -> new GroupTimeChangedDTO(group, membershipRepository))
                .collect(Collectors.toList());

        return groupTimeChangedDTOS.stream()
                .filter(gl -> {
                    Instant storedLastChange = excludedGroupsByTimeChanged.containsKey(gl.getGroupUid()) ?
                            Instant.ofEpochMilli(excludedGroupsByTimeChanged.get(gl.getGroupUid())) : Instant.MIN;
                    return storedLastChange.isBefore(gl.getLastGroupChange()); // keep eye out for microsecond differences...
                })
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<GroupMinimalDTO> fetchGroupNamesUidsOnly(String userUid, Set<String> groupUids) {
        if (groupUids == null || groupUids.isEmpty()) {
            return new HashSet<>();
        }
        final User user = userRepository.findOneByUid(userUid);

        return user.getMemberships().stream()
                .filter(membership -> groupUids.contains(membership.getGroup().getUid()))
                .map(membership -> new GroupMinimalDTO(membership.getGroup(), membership, this.membershipRepository))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public GroupFullDTO fetchGroupFullInfo(String userUid, String groupUid, boolean includeAllMembers, boolean includeAllSubgroups, boolean includeMemberHistory) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        final User user = userRepository.findOneByUid(userUid);
        final Group group = groupRepository.findOneByUid(groupUid);

        log.debug("fetching heavy group, group uid = {}, user = {}", groupUid, user.getName());

        final Membership membership = user.getMembership(group);
        if (membership == null) {
            log.error("Error! Non existent group or membership passed to query: group UID: {}", groupUid);
            return null;
        }
        final GroupFullDTO groupFullDTO = new GroupFullDTO(group, membership, this.membershipRepository);

        final boolean hasMemberDetailsPerm = permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        if (includeAllMembers && hasMemberDetailsPerm) {
            final Pageable page = PageRequest.of(0, MAX_DTO_MEMBERS, Sort.Direction.DESC, "joinTime");
            final Page<Membership> membershipPage = membershipRepository.findByGroupUid(group.getUid(), page);
            final List<Membership> memberships = membershipPage.getContent();
            final Set<MembershipDTO> membershipDTOS = memberships.stream().map(MembershipDTO::new).collect(Collectors.toSet());
            groupFullDTO.setMembers(membershipDTOS);
        }

        if (includeMemberHistory && hasMemberDetailsPerm) {
            final Instant sinceInstant = Instant.now().minus(180L, ChronoUnit.DAYS);
            final List<MembershipRecordDTO> membershipRecordDTOS = fetchRecentMembershipChanges(user.getUid(), group.getUid(), sinceInstant);
            groupFullDTO.setMemberHistory(membershipRecordDTOS);
        }

        if (includeAllSubgroups) {
            final List<Group> subgroups = groupRepository.findAll(Specification.where(hasParent(group)).and(isActive()));
            final List<GroupMembersDTO> groupMembersDTOS = subgroups.stream()
                    .map(g -> new GroupMembersDTO(g, membershipRepository))
                    .collect(Collectors.toList());
            groupFullDTO.setSubGroups(groupMembersDTOS);
        }

        if (hasMemberDetailsPerm) {
            long groupLogCount = groupLogRepository.count(GroupLogSpecifications.forInboundMessages(group, group.getCreatedDateTime(), Instant.now(), null));
            groupFullDTO.setHasInboundMessages(groupLogCount > 0);
        }

        log.info("heavy group info fetch, for group {}, members: {}, subgroups: {}, m history: {}, took {} msecs",
                group.getName(), includeAllMembers, includeAllSubgroups, includeMemberHistory, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return groupFullDTO;
    }

    @Override
    @Transactional(readOnly = true)
    public GroupFullDTO fetchSubGroupDetails(String userUid, String parentGroupUid, String childGroupUid) {
        final User user = userRepository.findOneByUid(userUid);

        final Membership parentGroupMembership = user.getMemberships().stream()
                .filter(membership -> membership.getGroup().getUid().equals(parentGroupUid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No membership group under UID " + parentGroupUid + " for user " + user));

        final Group parentGroup = parentGroupMembership.getGroup();
        final Group childGroup = groupRepository.findOneByUid(childGroupUid);

        if (!parentGroup.equals(childGroup.getParent())) {
            throw new IllegalArgumentException("Error! Parent/child mismatch");
        }

        if (permissionBroker.isGroupPermissionAvailable(user, parentGroup, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS) ||
                permissionBroker.isGroupPermissionAvailable(user, childGroup, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            final Membership membership = user.getMembershipOptional(childGroup).orElse(parentGroupMembership);
            return new GroupFullDTO(childGroup, membership, this.membershipRepository);
        } else {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipRecordDTO> fetchRecentMembershipChanges(String userUid, String groupUid, Instant fromDate) {
        Group group = groupRepository.findOneByUid(groupUid);
        User user = userRepository.findOneByUid(userUid);

        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }


        List<GroupLog> groupLogs = groupLogRepository.findAll(
                GroupLogSpecifications.memberChangeRecords(group, fromDate),
                new Sort(Sort.Direction.DESC, "createdDateTime"));

        // a little bit circuitous, but doing this to avoid possibly hundreds of separate calls
        // to group.getMembership (triggering single SQL calls) -- and in any case, keep profiling
        final Set<User> targetUsers = groupLogs.stream()
                .filter(GroupLog::hasTargetUser)
                .map(GroupLog::getTargetUser)
                .collect(Collectors.toSet());
        final List<Membership> memberships = membershipRepository.findByGroupAndUserIn(group, targetUsers);
        Map<Long, Membership> membershipMap = memberships.stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity()));

        return groupLogs.stream()
                .filter(log -> log.getUser() != null && log.getTargetUser() != null)
                .map(log -> new MembershipRecordDTO(membershipMap.get(log.getTargetUser().getId()), log))
                .sorted(Comparator.comparing(MembershipRecordDTO::getChangeDateTimeMillis, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Page<GroupLogDTO> getInboundMessageLogs(User user, Group group, Instant from, Instant to, String keyword, Pageable pageable) {
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        Page<GroupLog> page = groupLogRepository.findAll(Specification.where(GroupLogSpecifications.forInboundMessages(group, from, to, keyword)), pageable);
        return page.map(GroupLogDTO::new);
    }

    @Override
    @Transactional
    public List<GroupLogDTO> getInboundMessagesForExport(User user, Group group, Instant from, Instant to, String keyword) {
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        List<GroupLog> list = groupLogRepository.findAll(Specification.where(GroupLogSpecifications.forInboundMessages(group, from, to, keyword)));
        return list.stream().map(GroupLogDTO::new).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupWebDTO> fetchGroupWebInfo(String userUid, boolean includeSubgroups) {
        User user = userRepository.findOneByUid(userUid);

        List<Group> groups = groupRepository.findByMembershipsUserAndActiveTrueAndParentIsNull(user);

        List<GroupWebDTO> dtos = groups.stream()
                .map(gr -> {
                    final Membership membership = membershipRepository.findByGroupAndUser(gr, user);
                    List<GroupRefDTO> subGroups = includeSubgroups ? getSubgroups(gr) : Collections.emptyList();
                    return new GroupWebDTO(gr, membership, subGroups, this.membershipRepository);
                })
                .collect(Collectors.toList());

        return dtos.stream()
                .sorted(Comparator.comparing(GroupMinimalDTO::getLastTaskOrChangeTime, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupRefDTO> fetchGroupNamesUidsOnly(String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        // note: for present, since this is minimal, not even getting member count
        return groupRepository.findByMembershipsUserAndActiveTrueAndParentIsNull(user).stream()
                .map(group -> new GroupRefDTO(group, this.membershipRepository))
                .collect(Collectors.toList());
    }

    @Override
    public Page<Group> fetchGroupFiltered(String userUid, Permission requiredPermission, String searchTerm, Pageable pageable) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));

        // note : monitor this and if takes strain, optimize
        long startTime = System.currentTimeMillis();

        log.info("Fetching minimal groups, pageable = {}", pageable);
        Pageable pageRequest = pageable == null ? PageRequest.of(0, 10) : pageable;

        Page<Group> groupsFomDb;
        if (!StringUtils.isEmpty(searchTerm)) {
            final String tsEncodedTerm = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, true, true);
            log.info("For search term {}, encoded version {}", searchTerm, tsEncodedTerm);
            groupsFomDb = groupRepository.findUsersGroupsWithSearchTermOrderedByActivity(user.getId(), requiredPermission.name(), tsEncodedTerm, pageRequest);
        } else {
            log.info("No search term, but required permission: {}", requiredPermission);
            groupsFomDb = groupRepository.findUsersGroupsOrderedByActivity(user.getId(), requiredPermission.name(), pageRequest);
        }

        log.info("Content of page: {}", groupsFomDb.getContent());
        log.info("Time taken for WhatsApp group list: {} msecs", System.currentTimeMillis() - startTime);
        return groupsFomDb;
    }

    @Override
    public Page<Membership> fetchGroupMembers(User user, String groupUid, Pageable pageable) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        try {
            if (group.hasParent() && !user.isMemberOf(group)) {
                permissionBroker.validateGroupPermission(user, group.getParent(), Permission.GROUP_PERMISSION_CREATE_SUBGROUP);
            } else {
                permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
            }
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
        return membershipRepository.findByGroupUid(group.getUid(), pageable);
    }

    @Override
    public List<Membership> filterGroupMembers(User user, String groupUid,
                                               Collection<Province> provinces,
                                               Boolean noProvince,
                                               Collection<String> taskTeamsUids,
                                               Collection<String> topics,
                                               Collection<String> affiliations,
                                               Collection<GroupJoinMethod> joinMethods,
                                               Collection<String> joinedCampaignsUids,
                                               Integer joinDaysAgo,
                                               LocalDate joinDate,
                                               JoinDateCondition joinDateCondition,
                                               String namePhoneOrEmail, Collection<String> languages) {

        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);

        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }

        log.info("filtering on, user name: {}, provinces: {}, taskTeams: {}", namePhoneOrEmail, provinces, taskTeamsUids);
        List<Membership> members = membershipRepository.findAll(
                MembershipSpecifications.filterGroupMembership(group, provinces, noProvince, taskTeamsUids, joinMethods, joinDaysAgo, joinDate, joinDateCondition, namePhoneOrEmail, languages)
        );

        log.info("post-filtering, have {} members", members.size());

        if(topics != null && topics.size() > 0){
            // this is an "and" filter, at present
            members = members.stream()
            .filter(m -> m.getTopics().containsAll(topics))
            .collect(Collectors.toList());
        }

        if (affiliations != null && !affiliations.isEmpty()) {
            // i.e., this is an "or" filtering
            Set<String> affils = new HashSet<>(affiliations);
            log.info("filtering {} members, looking for affiliations {}", members.size(), affils.toString());
            members = members.stream().filter(m -> m.getAffiliations().stream().anyMatch(affils::contains))
                    .collect(Collectors.toList());
        }

        //this is an alternative to very complicated query
        if (joinedCampaignsUids != null && joinedCampaignsUids.size() > 0) {
            List<CampaignLog> campLogs = campaignLogRepository.findByCampaignLogTypeAndCampaignMasterGroupUid(CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP, groupUid);
            campLogs = campLogs.stream().filter(cl -> joinedCampaignsUids.contains(cl.getCampaign().getUid())).collect(Collectors.toList());
            List<User> usersAddedByCampaigns = campLogs.stream().map(cl -> cl.getUser()).collect(Collectors.toList());
            members = members.stream().filter( m -> usersAddedByCampaigns.contains(m.getUser())).collect(Collectors.toList());
        }

        return members;
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipFullDTO fetchGroupMember(String userUid, String groupUid, String memberUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        if (!permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS) &&
                !(group.hasParent() && permissionBroker.isGroupPermissionAvailable(user, group.getParent(), Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS))) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
        final Membership membership = membershipRepository.findByGroupUidAndUserUid(groupUid, memberUid);
        return new MembershipFullDTO(membership, this.membershipRepository);
    }

    @Override
    public Page<Membership> fetchUserGroupsNewMembers(User user, Instant from, Pageable pageable) {
        List<Long> groupsWhereUserCanSeeMemberDetails = groupRepository.findGroupIdsWhereMemberHasPermission(user, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        if (!groupsWhereUserCanSeeMemberDetails.isEmpty()) {
            Specification<Membership> spec = MembershipSpecifications
                    .recentMembershipsInGroups(groupsWhereUserCanSeeMemberDetails, from, user);
            return membershipRepository.findAll(spec, pageable);
        } else {
            return new PageImpl<>(new ArrayList<>());
        }
    }

    @Override
    public Group fetchGroupByGroupUid(String groupUid) {
       return groupRepository.findOneByUid(groupUid);
    }

    @Override
    public List<ActionLog> fetchUserActivityDetails(String queryingUserUid, String groupUid, String memberUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        User qUser = userRepository.findOneByUid(queryingUserUid);

        permissionBroker.validateGroupPermission(qUser, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

        Membership membership = membershipRepository.findByGroupUidAndUserUid(groupUid, memberUid);

        // and add a bunch more too ...
        return logsBroker.fetchMembershipLogs(membership);
    }

    private List<GroupRefDTO> getSubgroups(Group group) {
        final List<Group> groups = groupRepository.findAll(Specification.where(hasParent(group)).and(isActive()));
        return groups.stream()
                .map(gr -> new GroupRefDTO(gr, this.membershipRepository))
                .collect(Collectors.toList());
    }

}
