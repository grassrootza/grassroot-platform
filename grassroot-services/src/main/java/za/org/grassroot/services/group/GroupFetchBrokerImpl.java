package za.org.grassroot.services.group;

import com.codahale.metrics.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.*;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.GroupSpecifications.hasParent;
import static za.org.grassroot.core.specifications.GroupSpecifications.isActive;

/**
 * Primary class for new style group feching, watch performance very closely (e.g., on size of memberships)
 */
@Service @Slf4j
public class GroupFetchBrokerImpl implements GroupFetchBroker {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupLogRepository groupLogRepository;
    private final MembershipRepository membershipRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final PermissionBroker permissionBroker;
    private final LogsAndNotificationsBroker logsBroker;

    private final EntityManager entityManager;

    @Autowired
    public GroupFetchBrokerImpl(UserRepository userRepository, GroupRepository groupRepository,
                                GroupLogRepository groupLogRepository, MembershipRepository membershipRepository,
                                CampaignLogRepository campaignLogRepository, LogsAndNotificationsBroker logsBroker,
                                PermissionBroker permissionBroker, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupLogRepository = groupLogRepository;
        this.membershipRepository = membershipRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.permissionBroker = permissionBroker;
        this.logsBroker = logsBroker;
        this.entityManager = entityManager;
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged) {
        User user = userRepository.findOneByUid(userUid);

        // major todo: do all this through specifications instead of typed query, and consolidate to one query
        Specifications<Group> specifications = Specifications
                .where(GroupSpecifications.isActive())
                .and(GroupSpecifications.userIsMember(user));

        if (excludedGroupsByTimeChanged != null && !excludedGroupsByTimeChanged.isEmpty()) {
            specifications = specifications.and(
                    Specifications.not(GroupSpecifications.uidIn(excludedGroupsByTimeChanged.keySet())));
        }

        Set<String> newGroupUids = groupRepository.findAll(specifications)
                .stream()
                .map(Group::getUid)
                .collect(Collectors.toSet());

        Set<String> uidsToLookUp = new HashSet<>(excludedGroupsByTimeChanged.keySet());
        uidsToLookUp.addAll(newGroupUids);

        TypedQuery<GroupTimeChangedDTO> changedGroupQuery = entityManager.createQuery("" +
                "select new za.org.grassroot.core.dto.group.GroupTimeChangedDTO(g, g.lastGroupChangeTime) " +
                "from Group g where g.uid in :groupUids", GroupTimeChangedDTO.class)
                .setParameter("groupUids", uidsToLookUp);

        return changedGroupQuery.getResultList()
                .stream()
                .filter(gl -> {
                    Instant storedLastChange = excludedGroupsByTimeChanged.containsKey(gl.getGroupUid()) ?
                            Instant.ofEpochMilli(excludedGroupsByTimeChanged.get(gl.getGroupUid())) : Instant.MIN;
                    return storedLastChange.isBefore(gl.getLastGroupChange()); // keep eye out for microsecond differences...
                })
                .collect(Collectors.toSet());
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public Set<GroupMinimalDTO> fetchGroupMinimalInfo(String userUid, Set<String> groupUids) {
        if (groupUids == null || groupUids.isEmpty()) {
            return new HashSet<>();
        }
        User user = userRepository.findOneByUid(userUid);
        List<GroupMinimalDTO> dtoList = entityManager.createQuery("" +
                "select new za.org.grassroot.core.dto.group.GroupMinimalDTO(g, m) " +
                "from Group g inner join g.memberships m " +
                "where g.uid in :groupUids and m.user = :user", GroupMinimalDTO.class)
                .setParameter("groupUids", groupUids)
                .setParameter("user", user)
                .getResultList();

        return new HashSet<>(dtoList);
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public List<GroupMinimalDTO> fetchAllUserGroupsSortByLatestTime(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        // there is almost certainly a way to do order by the max of the two timestamps in query but it is late and HQL
        List<GroupMinimalDTO> dtos = entityManager.createQuery("" +
                "select new za.org.grassroot.core.dto.group.GroupMinimalDTO(g, m) " +
                "from Group g inner join g.memberships m " +
                "where g.active = true and m.user = :user", GroupMinimalDTO.class)
                .setParameter("user", user)
                .getResultList();

        return dtos.stream()
                .sorted(Comparator.comparing(GroupMinimalDTO::getLastTaskOrChangeTime, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public GroupFullDTO fetchGroupFullInfo(String userUid, String groupUid) {
        long startTime = System.currentTimeMillis();
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        List<GroupFullDTO> dtoResults = entityManager.createQuery("" +
                "select new za.org.grassroot.core.dto.group.GroupFullDTO(g, m) " +
                "from Group g inner join g.memberships m " +
                "where g.uid = :groupUid and m.user = :user", GroupFullDTO.class)
                .setParameter("groupUid", groupUid)
                .setParameter("user", user)
                .getResultList();

        if (dtoResults.isEmpty()) {
            throw new IllegalArgumentException("Error! Non existent group passed to query");
        }

        GroupFullDTO groupFullDTO = dtoResults.get(0);

        if (permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            groupFullDTO.setMemberHistory(fetchRecentMembershipChanges(user.getUid(), group.getUid(),
                    Instant.now().minus(180L, ChronoUnit.DAYS)));
        }

        log.info("heavy group info fetch, took {} msecs", System.currentTimeMillis() - startTime);
        return groupFullDTO;
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public GroupFullDTO fetchGroupFullDetails(String userUid, String groupUid) {
        Group group = entityManager.createQuery("" +
                "select g " +
                "from Group g inner join g.memberships m " +
                "where g.uid = :groupUid and m.user.uid = :userUid", Group.class)
                .setParameter("groupUid", groupUid)
                .setParameter("userUid", userUid)
                .getSingleResult();

        GroupFullDTO dto = new GroupFullDTO(group, group.getMembership(userUid));
        dto.setSubGroups(groupRepository.findAll(Specifications.where(hasParent(group)).and(isActive()))
                .stream().map(GroupMembersDTO::new).collect(Collectors.toList()));
        return dto;
    }

    @Override
    public GroupFullDTO fetchSubGroupDetails(String userUid, String parentUid, String taskTeamUid) {
        Group parent = entityManager.createQuery("" +
                "select g " +
                "from Group g inner join g.memberships m " +
                "where g.uid = :groupUid and m.user.uid = :userUid", Group.class)
                .setParameter("groupUid", parentUid)
                .setParameter("userUid", userUid)
                .getSingleResult();

        User user = userRepository.findOneByUid(userUid);
        Group child = groupRepository.findOneByUid(taskTeamUid);

        if (!parent.equals(child.getParent())) {
            throw new IllegalArgumentException("Error! Parent/child mismatch");
        }

        if (!permissionBroker.isGroupPermissionAvailable(user, parent, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS) &&
                !permissionBroker.isGroupPermissionAvailable(user, child, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }

        Membership membership = membershipRepository.findByGroupUidAndUserUid(taskTeamUid, userUid);
        if (membership == null) {
            membership = membershipRepository.findByGroupUidAndUserUid(parentUid, userUid);
        }

        return new GroupFullDTO(child, membership);
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
        List<Membership> memberships = membershipRepository.findByGroupAndUserIn(group, groupLogs.stream()
                .filter(GroupLog::hasTargetUser)
                .map(GroupLog::getTargetUser)
                .collect(Collectors.toList()));
        Map<Long, Membership> membershipMap = memberships.stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity()));

        return groupLogs.stream()
                .filter(log -> log.getUser() != null && log.getTargetUser() != null)
                .map(log -> new MembershipRecordDTO(membershipMap.get(log.getTargetUser().getId()), log))
                .sorted(Comparator.comparing(MembershipRecordDTO::getChangeDateTimeMillis, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public List<GroupWebDTO> fetchGroupWebInfo(String userUid) {

        User user = userRepository.findOneByUid(userUid);

        List<Group> groups = groupRepository.findByMembershipsUserAndActiveTrueAndParentIsNull(user);
        List<GroupWebDTO> dtos = groups.stream().map(gr -> new GroupWebDTO(gr, gr.getMembership(user), getSubgroups(gr))).collect(Collectors.toList());

        return dtos.stream()
                .sorted(Comparator.comparing(GroupMinimalDTO::getLastTaskOrChangeTime, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    @Override
    public Page<MembershipFullDTO> fetchGroupMembers(User user, String groupUid, Pageable pageable) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);
        try {
            if (group.hasParent() && !group.hasMember(user)) {
                permissionBroker.validateGroupPermission(user, group.getParent(), Permission.GROUP_PERMISSION_CREATE_SUBGROUP);
            } else {
                permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
            }
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
        Page<Membership> members = membershipRepository.findByGroupUid(group.getUid(), pageable);
        return members.map(MembershipFullDTO::new);
    }

    @Override
    public List<Membership> filterGroupMembers(User user, String groupUid,
                                                      Collection<Province> provinces,
                                                      Collection<String> taskTeamsUids,
                                                      Collection<String> topics,
                                                      Collection<String> affiliations,
                                                      Collection<GroupJoinMethod> joinMethods,
                                                      Collection<String> joinedCampaignsUids,
                                                      Integer joinDaysAgo,
                                                      LocalDate joinDate,
                                                      JoinDateCondition joinDateCondition,
                                                      String namePhoneOrEmail) {

        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);

        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }

        log.info("filtering on, provinces: {}, taskTeams: {}", provinces, taskTeamsUids);
        List<Membership> members = membershipRepository.findAll(
                MembershipSpecifications.filterGroupMembership(group, provinces, taskTeamsUids, joinMethods, joinDaysAgo, joinDate, joinDateCondition, namePhoneOrEmail)
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
        return new MembershipFullDTO(membershipRepository.findByGroupUidAndUserUid(groupUid, memberUid));
    }

    @Override
    public Page<Membership> fetchUserGroupsNewMembers(User user, Instant from, Pageable pageable) {
        List<Group> groupsWhereUserCanSeeMemberDetails = groupRepository.findAll(GroupSpecifications.userIsMemberAndCanSeeMembers(user));
        if (groupsWhereUserCanSeeMemberDetails != null && !groupsWhereUserCanSeeMemberDetails.isEmpty()) {
            Specifications<Membership> spec = MembershipSpecifications
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
        return groupRepository.findAll(Specifications.where(hasParent(group)).and(isActive()))
                .stream().map(gr -> new GroupRefDTO(gr.getUid(), gr.getGroupName(), gr.getMemberships().size()))
                .collect(Collectors.toList());
    }

}
