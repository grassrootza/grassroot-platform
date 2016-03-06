package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupBrokerImpl implements GroupBroker {

    private final Logger logger = LoggerFactory.getLogger(GroupBrokerImpl.class);

    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PermissionsManagementService permissionsManagementService;

    @Override
    @Transactional
    public Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos, GroupPermissionTemplate groupPermissionTemplate) {

        Objects.requireNonNull(userUid);
        Objects.requireNonNull(name);
        Objects.requireNonNull(membershipInfos);
        Objects.requireNonNull(groupPermissionTemplate);

        User user = userRepository.findOneByUid(userUid);

        Group parent = null;
        if (parentGroupUid != null) {
            parent = groupRepository.findOneByUid(parentGroupUid);
        }

        logger.info("Creating new group: name={}, membershipInfos={}, groupPermissionTemplate={},  parent={}, user={}",
                name, membershipInfos, groupPermissionTemplate, parent, user);

        Group group = new Group(name, user);
        if (parent != null) {
            group.setParent(parent);
        }
        addMembers(user, group, membershipInfos);
        permissionsManagementService.setRolePermissionsFromTemplate(group, groupPermissionTemplate);
        group = groupRepository.save(group);

        logger.info("Group created under UID {}", group.getUid());

        return group;
    }

    @Override
    @Transactional
    public void updateName(String userUid, String groupUid, String name) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setGroupName(name);
    }

    @Override
    @Transactional
    public void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        logger.info("Adding members: group={}, memberships={}, user={}", group, membershipInfos, user);
        addMembers(user, group, membershipInfos);
    }

    private void addMembers(User initiator, Group group, Set<MembershipInfo> membershipInfos) {
        // note: User objects should only ever store phone numbers in the msisdn format (i.e, with country code at front, no '+')
        Set<String> memberPhoneNumbers = membershipInfos.stream().map(MembershipInfo::getPhoneNumberWithCCode).collect(Collectors.toSet());
        logger.info("phoneNumbers returned: ...." + memberPhoneNumbers);
        Set<User> existingUsers = new HashSet<>(userRepository.findByPhoneNumberIn(memberPhoneNumbers));
        Map<String, User> existingUserMap = existingUsers.stream().collect(Collectors.toMap(User::getPhoneNumber, user -> user));

        for (MembershipInfo membershipInfo : membershipInfos) {
            User user = existingUserMap.getOrDefault(membershipInfo.getPhoneNumberWithCCode(), new User(membershipInfo.getPhoneNumberWithCCode(), membershipInfo.getDisplayName()));
            String roleName = membershipInfo.getRoleName();
            if (roleName == null) {
                group.addMember(user);
            } else {
                group.addMember(user, roleName);
            }
        }
    }

    @Override
    @Transactional
    public void removeMembers(String userUid, String groupUid, Set<String> memberUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        logger.info("Removing members: group={}, memberUids={}, user={}", group, memberUids, user);

        group.getMemberships().stream()
                .filter(membership -> memberUids.contains(membership.getUser().getUid()))
                .map(Membership::getUser)
                .forEach(group::removeMember);

        groupRepository.save(group);
    }

    @Override
    @Transactional
    public void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Membership membership = group.getMemberships().stream()
                .filter(membership1 -> membership1.getUser().getUid().equals(memberUid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no member under UID " + memberUid + " in group " + group));

        logger.info("Updating membership role: membership={}, roleName={}, user={}", membership, roleName, user);

        Role role = group.getRole(roleName);
        membership.setRole(role);
    }
}
