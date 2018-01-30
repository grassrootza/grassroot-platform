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
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.*;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupLogSpecifications;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
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
    private final PermissionBroker permissionBroker;
    private final EntityManager entityManager;

    @Autowired
    public GroupFetchBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, GroupLogRepository groupLogRepository, MembershipRepository membershipRepository, PermissionBroker permissionBroker, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupLogRepository = groupLogRepository;
        this.membershipRepository = membershipRepository;
        this.permissionBroker = permissionBroker;
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
        dto.setSubGroups(getSubgroups(group));
        return dto;
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
                .filter(log -> log.getUser() != null)
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
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
        Page<Membership> members = membershipRepository.findByGroupUid(group.getUid(), pageable);
        return members.map(MembershipFullDTO::new);
    }

    @Override
    public List<MembershipFullDTO> filterGroupMembers(User user, String groupUid,
                                                      Collection<Province> provinces,
                                                      Collection<String> taskTeamsUids,
                                                      Collection<String> topics,
                                                      Collection<GroupJoinMethod> joinMethods,
                                                      Collection<String> joinedCampaignsUids
    ) {
        Objects.requireNonNull(groupUid);
        Group group = groupRepository.findOneByUid(groupUid);


        try{
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }

        List<Membership> members = membershipRepository.findAll(
                MembershipSpecifications.filterGroupMembership(group, provinces, taskTeamsUids, joinMethods, joinedCampaignsUids)
        );

        if(topics != null && topics.size() > 0){
            members = members.stream()
            .filter(m -> m.getTopics().containsAll(topics))
            .collect(Collectors.toList());
        }
        return members.stream().map(MembershipFullDTO::new).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipFullDTO fetchGroupMember(String userUid, String groupUid, String memberUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }
        return new MembershipFullDTO(membershipRepository.findByGroupUidAndUserUid(groupUid, memberUid));
    }

    @Override
    public Page<Membership> fetchUserGroupsNewMembers(User user, Instant from, Pageable pageable) {
        List<Group> groupsWhereUserCanSeeMemberDetails = groupRepository.findAll(GroupSpecifications.userIsMemberAndCanSeeMembers(user));
        if (groupsWhereUserCanSeeMemberDetails != null && !groupsWhereUserCanSeeMemberDetails.isEmpty()) {
            Specifications<Membership> spec = MembershipSpecifications.recentMembershipsInGroups(groupsWhereUserCanSeeMemberDetails, from);
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

        User tUser = userRepository.findOneByUid(memberUid);

        List<GroupLog> groupLogs = groupLogRepository.findAll(
                GroupLogSpecifications.memberChangeRecords(group, DateTimeUtil.getEarliestInstant())
                        .and(GroupLogSpecifications.containingUser(tUser)));

        // and add a bunch more too ...

        return groupLogs.stream().map(gl -> (ActionLog) gl).collect(Collectors.toList());
    }

    private List<GroupRefDTO> getSubgroups(Group group) {
        return groupRepository.findAll(Specifications.where(hasParent(group)).and(isActive()))
                .stream().map(gr -> new GroupRefDTO(gr.getUid(), gr.getGroupName(), gr.getMemberships().size()))
                .collect(Collectors.toList());
    }

}
