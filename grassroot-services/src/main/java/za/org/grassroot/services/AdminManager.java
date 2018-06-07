package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.group.*;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.UserSpecifications;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.graph.GraphBroker;
import za.org.grassroot.services.group.GroupBroker;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    private final UserLogRepository userLogRepository;
    private final PasswordEncoder passwordEncoder;

    private GraphBroker graphBroker;

    @Autowired
    public AdminManager(UserRepository userRepository, GroupRepository groupRepository, RoleRepository roleRepository, GroupBroker groupBroker, GroupLogRepository groupLogRepository, UserLogRepository userLogRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.roleRepository = roleRepository;
        this.groupBroker = groupBroker;
        this.groupLogRepository = groupLogRepository;
        this.userLogRepository = userLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired(required = false)
    public void setGraphBroker(GraphBroker graphBroker) {
        this.graphBroker = graphBroker;
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

        groupLogRepository.save(new GroupLog(group, user, GroupLogType.GROUP_REMOVED,
                active ? "Activated by system admin" : "Deactivated by system admin"));
    }

    @Override
    @Transactional
    public void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo) {
        validateAdminRole(adminUserUid);
        groupBroker.addMembers(adminUserUid, groupUid, Collections.singleton(membershipInfo),
                GroupJoinMethod.ADDED_BY_SYS_ADMIN, true);
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

    @Override
    @Transactional
    public void addSystemRole(String adminUserUid, String userUid, String systemRole) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Role role = roleRepository.findByName(BaseRoles.ROLE_ALPHA_TESTER).get(0);
        user.addStandardRole(role);
        userLogRepository.save(new UserLog(userUid, UserLogType.GRANTED_SYSTEM_ROLE,
                systemRole + " granted by admin. uid::" + adminUserUid, UserInterfaceType.WEB));
    }

    @Override
    @Transactional
    public void removeStdRole(String adminUserUid, String userUid, String systemRole) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Role role = roleRepository.findByNameAndRoleType(systemRole, Role.RoleType.STANDARD).get(0);
        logger.info("found a role? : {}, and a user : {}", role, user);
        user.removeStandardRole(role);
        userLogRepository.save(new UserLog(userUid, UserLogType.REVOKED_SYSTEM_ROLE,
                systemRole + " removed by admin. uid::" + userUid, UserInterfaceType.WEB));
    }

    @Override
    @Transactional
    public String createUserWithSystemRole(String adminUserUid, String displayName, String phoneNumber, String emailAddress, String systemRole) {
        validateAdminRole(adminUserUid);
        if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(emailAddress)) {
            throw new IllegalArgumentException("Error! One of email or phone number must be non-empty");
        }
        String msisdn = StringUtils.isEmpty(phoneNumber) ? null : PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        User user = msisdn != null && userRepository.existsByPhoneNumber(msisdn) ? userRepository.findByPhoneNumberAndPhoneNumberNotNull(msisdn) :
                userRepository.findByEmailAddressAndEmailAddressNotNull(emailAddress);
        if (user == null) {
            user = new User(msisdn, displayName, emailAddress);
            userRepository.saveAndFlush(user);
            userLogRepository.save(new UserLog(user.getUid(), UserLogType.CREATED_IN_DB,
                    "created by admin, uid : " + adminUserUid, UserInterfaceType.WEB));
        } else {
            if (!StringUtils.isEmpty(displayName)) {
                user.setDisplayName(displayName);
            }
            if (!StringUtils.isEmpty(msisdn)) {
                user.setPhoneNumber(msisdn);
            }
            if (!StringUtils.isEmpty(emailAddress)) {
                user.setEmailAddress(emailAddress);
            }
        }
        addSystemRole(adminUserUid, user.getUid(), systemRole);
        return user.getUid();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUsersWithStdRole(String adminUserUid, String systemRole) {
        validateAdminRole(adminUserUid);
        return userRepository.findAll(UserSpecifications.hasStandardRole(systemRole));
    }

    @Override
    @Transactional
    public void updateUserPassword(String adminUserUid, String userUid, String newPassword) {
        Objects.requireNonNull(adminUserUid);
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(newPassword);

        validateAdminRole(adminUserUid);

        User user = userRepository.findOneByUid(userUid);
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        userLogRepository.save(new UserLog(user.getUid(), UserLogType.ADMIN_CHANGED_PASSWORD,
                adminUserUid, UserInterfaceType.WEB));
    }

    @Override
    @Transactional(readOnly = true)
    public void populateGrassrootGraphUsers() {
        if (graphBroker != null) {
            Specifications<User> users = Specifications.where((root, query, cb) -> cb.isTrue(root.get(User_.enabled)));
            userRepository.findAll(users).forEach(user -> graphBroker.addUserToGraph(user.getUid()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void populateGrassrootGraphGroups() {
        if (graphBroker != null) {
            Specifications<Group> groups = Specifications.where((root, query, cb) -> cb.isTrue(root.get(Group_.active)));
            groupRepository.findAll(groups).forEach(group -> graphBroker.addGroupToGraph(group.getUid(), group.getCreatedByUser().getUid()));
        }
    }

    private void validateAdminRole(String adminUserUid) {
        User admin = userRepository.findOneByUid(adminUserUid);
        Role adminRole = roleRepository.findByName(BaseRoles.ROLE_SYSTEM_ADMIN).get(0);
        if (!admin.getStandardRoles().contains(adminRole)) {
            throw new AccessDeniedException("Error! User does not have admin role");
        }
    }

}
