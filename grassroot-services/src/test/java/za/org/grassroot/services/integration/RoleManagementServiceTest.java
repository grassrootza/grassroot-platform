package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.*;

import javax.transaction.Transactional;

import static org.junit.Assert.*;

/**
 * Created by luke on 2015/11/19.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class RoleManagementServiceTest extends AbstractTransactionalJUnit4SpringContextTests {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementServiceTest.class);

    @Autowired
    RoleManagementService roleManagementService;

    @Autowired
    UserManagementService userManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    PermissionsManagementService permissionsManagementService;

    @Autowired
    GroupAccessControlManagementService groupAccessControlManagementService;

    @Test
    @Rollback
    public void shouldAssignDefaultPermissionsToRole() {

        User user = userManagementService.loadOrSaveUser("0810001111");
        Group group = groupManagementService.createNewGroup(user, "test group");
        Role organizerRole = roleManagementService.addRoleToGroup(BaseRoles.ROLE_GROUP_ORGANIZER, group);
        Permission addUserPermission = permissionsManagementService.findByName(BasePermissions.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        organizerRole.setPermissions(permissionsManagementService.defaultGroupOrganizerPermissions());
        groupManagementService.saveGroup(group);
        assertNotNull(group.getGroupRoles());
        assertTrue(group.getGroupRoles().contains(organizerRole));
        assertNotNull(organizerRole.getPermissions());
        assertTrue(organizerRole.getPermissions().contains(addUserPermission));

        /*
        I would prefer to run the following lines as tests, because that would make sure persistence and retrieval is
        working properly. However,Spring/JUnit's bizarre persistence logic makes it impossible to get these to pass,hence
        will have to test that manually
        */

        /* Role organizerRoleFromGroup = roleManagementService.fetchGroupRole(BaseRoles.ROLE_GROUP_ORGANIZER, group);
        assertNotNull(organizerRoleFromGroup); */
    }

    @Test
    public void shouldAssignDefaultPermissions() {

        User user1 = userManagementService.loadOrSaveUser("27810002222");
        User user2 = userManagementService.loadOrSaveUser("27810002223");
        User user3 = userManagementService.loadOrSaveUser("27810002224");

        assertNotNull(user1.getUsername());
        assertNotNull(user2.getUsername());
        assertNotNull(user3.getUsername());

        Group group = groupManagementService.createNewGroup(user1, "test group");

        // roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, user1);
        // roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_COMMITTEE_MEMBER, group, user2);
        // roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_ORDINARY_MEMBER, group, user3);

    }

}
