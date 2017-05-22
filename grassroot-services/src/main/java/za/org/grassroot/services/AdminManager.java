package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.exception.MemberNotPartOfGroupException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.group.GroupBroker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/02/04.
 */
@Service
public class AdminManager implements AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminManager.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final GroupBroker groupBroker;
    private final GroupLogRepository groupLogRepository;

    @Autowired
    public AdminManager(UserRepository userRepository, GroupRepository groupRepository, RoleRepository roleRepository, GroupBroker groupBroker, GroupLogRepository groupLogRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.roleRepository = roleRepository;
        this.groupBroker = groupBroker;
        this.groupLogRepository = groupLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber) {
        return maskListUsers(userRepository.findByDisplayNameContainingOrPhoneNumberContaining(inputNumber, inputNumber));
    }

    /**
     * SECTION: METHODS TO HANDLE GROUPS
     */

    @Override
    @Transactional
    public void updateGroupActive(String adminUserUid, String groupUid, boolean active) {
        validateAdminRole(adminUserUid);

        User user = userRepository.findOneByUid(adminUserUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setActive(active);

        groupLogRepository.save(new GroupLog(group, user, GroupLogType.GROUP_REMOVED, null,
                active ? "Activated by system admin" : "Deactivated by system admin"));
    }

    @Override
    @Transactional
    public void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo) {
        validateAdminRole(adminUserUid);
        groupBroker.addMembers(adminUserUid, groupUid, Collections.singleton(membershipInfo), true);
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(String adminUserUid, String groupUid, String memberMsisdn) {
        // this is a 'quiet' operation, since only ever triggered if receive an "OPT OUT" notification ... hence don't group log etc
        validateAdminRole(adminUserUid);
        Group group = groupRepository.findOneByUid(groupUid);
        User member = userRepository.findByPhoneNumber(memberMsisdn);
        if (member == null) {
            throw new NoSuchUserException("User with that phone number does not exist");
        }
        Membership membership = group.getMembership(member);
        if (membership == null) {
            throw new MemberNotPartOfGroupException();
        } else {
            group.removeMembership(membership);
        }
    }

    @Override
    @Transactional
    public void removeUserFromAllGroups(String adminUserUid, String userUid) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Set<Membership> memberships = user.getMemberships();
        logger.info("admin user now removing user from {} groups", memberships.size());
        for (Membership membership : memberships) {
            Group group = membership.getGroup();
            group.removeMembership(membership); // concurrency?
        }
    }

    private void validateAdminRole(String adminUserUid) {
        User admin = userRepository.findOneByUid(adminUserUid);
        Role adminRole = roleRepository.findByName(BaseRoles.ROLE_SYSTEM_ADMIN).get(0);
        if (!admin.getStandardRoles().contains(adminRole)) {
            throw new AccessDeniedException("Error! User does not have admin role");
        }
    }

    /*
   Helper functions to mask a list of entities
    */
    private List<MaskedUserDTO> maskListUsers(List<User> users) {
        List<MaskedUserDTO> maskedUsers = new ArrayList<>();
        for (User user : users)
            maskedUsers.add(new MaskedUserDTO(user));
        return maskedUsers;
    }

}
