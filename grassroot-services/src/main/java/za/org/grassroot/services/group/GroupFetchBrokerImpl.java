package za.org.grassroot.services.group;

import com.codahale.metrics.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipDTO;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.GroupSpecifications.hasParent;
import static za.org.grassroot.core.specifications.GroupSpecifications.isActive;

/**
 * Primary class for new style group feching, watch performance very closely (e.g., on size of memberships)
 */
@Service
public class GroupFetchBrokerImpl implements GroupFetchBroker {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final PermissionBroker permissionBroker;
    private final EntityManager entityManager;

    @Autowired
    public GroupFetchBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, MembershipRepository membershipRepository, PermissionBroker permissionBroker, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
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
                "from Group g inner join g.memberships m" +
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
    public Set<GroupFullDTO> fetchGroupFullInfo(String userUid, Set<String> groupUids) {
        if (groupUids == null || groupUids.isEmpty()) {
            return new HashSet<>();
        }
        User user = userRepository.findOneByUid(userUid);
        return new HashSet<>(entityManager.createQuery("" +
                "select new za.org.grassroot.core.dto.group.GroupFullDTO(g, m) " +
                "from Group g inner join g.memberships m " +
                "where g.uid in :groupUids and m.user = :user", GroupFullDTO.class)
                .setParameter("groupUids", groupUids)
                .setParameter("user", user)
                .getResultList());
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
    public Set<MembershipDTO> fetchGroupMembershipInfo(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);
        }

        return group.getMemberships().stream().map(MembershipDTO::new).collect(Collectors.toSet());
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


    private List<GroupRefDTO> getSubgroups(Group group) {
        return groupRepository.findAll(Specifications.where(hasParent(group)).and(isActive()))
                .stream().map(gr -> new GroupRefDTO(gr.getUid(), gr.getGroupName(), gr.getMemberships().size()))
                .collect(Collectors.toList());
    }

}
