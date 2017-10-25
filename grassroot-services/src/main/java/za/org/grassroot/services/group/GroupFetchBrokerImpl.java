package za.org.grassroot.services.group;

import com.codahale.metrics.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Primary class for new style group feching, watch performance very closely (e.g., on size of memberships)
 */
@Service
public class GroupFetchBrokerImpl implements GroupFetchBroker {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final EntityManager entityManager;

    @Autowired
    public GroupFetchBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, MembershipRepository membershipRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
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
                "select new za.org.grassroot.core.dto.group.GroupTimeChangedDTO(g.uid, g.lastGroupChangeTime) " +
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
                        "select new za.org.grassroot.core.dto.group.GroupMinimalDTO(g, r) " +
                        "from Group g inner join g.memberships m inner join m.role r " +
                        "where g.uid in :groupUids and m.user = :user", GroupMinimalDTO.class)
                .setParameter("groupUids", groupUids)
                .setParameter("user", user)
                .getResultList();

        // as below, a sufficiently ninja subquery count in the constructor above should take care of this, but leaving till later
        return dtoList.stream()
                .map(gdto -> gdto.addMemberCount(membershipRepository.countByGroupUid(gdto.getGroupUid())))
                .collect(Collectors.toSet());
    }

    @Timed
    @Override
    @Transactional(readOnly = true)
    public List<GroupMinimalDTO> fetchAllUserGroupsSortByLatestTime(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        // there is almost certainly a way to do order by the max of the two timestamps in query but it is late and HQL
        List<GroupMinimalDTO> dtos = entityManager.createQuery("" +
                "select new za.org.grassroot.core.dto.group.GroupMinimalDTO(g, r) " +
                "from Group g inner join g.memberships m inner join m.role r " +
                "where g.active = true and m.user = :user", GroupMinimalDTO.class)
                .setParameter("user", user)
                .getResultList();

        // if hql wasn't hql, could just do this in a subquery above, but tft
        dtos.forEach(gdto -> gdto.setMemberCount(membershipRepository.countByGroupUid(gdto.getGroupUid())));

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


}
