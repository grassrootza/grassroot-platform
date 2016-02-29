package za.org.grassroot.services;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupBrokerImpl implements GroupBroker {
    private GroupRepository groupRepository;
    private RoleRepository roleRepository;
    private UserRepository userRepository;

    @Override
    @Transactional
    public String create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos) {
        User user = userRepository.findOneByUid(userUid);

        Group group = new Group(name, user);
        if (parentGroupUid != null) {
            Group parent = groupRepository.findOneByUid(parentGroupUid);
            group.setParent(parent);
        }
        addMembers(user, group, membershipInfos);

        return group.getUid();
    }

    @Override
    public void update(String userUid, String groupUid, String name) {

    }

    @Override
    @Transactional
    public void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        addMembers(user, group, membershipInfos);
    }

    private void addMembers(User initiator, Group group, Set<MembershipInfo> membershipInfos) {
        Set<String> memberPhoneNumbers = membershipInfos.stream().map(MembershipInfo::getPhoneNumber).collect(Collectors.toSet());
        Set<User> existingUsers = findAllUsersByPhoneNumbers(memberPhoneNumbers);
        Map<String, User> existingUserMap = existingUsers.stream().collect(Collectors.toMap(User::getPhoneNumber, user -> user));

        for (MembershipInfo membershipInfo : membershipInfos) {
            User user = existingUserMap.getOrDefault(membershipInfo.getPhoneNumber(), new User(membershipInfo.getPhoneNumber(), membershipInfo.getDisplayName()));
            Role role = membershipInfo.getRoleId() == null ? null : roleRepository.findOne(membershipInfo.getRoleId());
            group.addMember(user); // todo: add role
        }
    }

    @Override
    @Transactional
    public void removeMembers(String userUid, String groupUid, Set<String> memberUids) {

    }

    @Override
    public void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleId) {

    }

    private Set<User> findAllUsersByPhoneNumbers(Set<String> phoneNumbers) {
        return null;
    }
}
