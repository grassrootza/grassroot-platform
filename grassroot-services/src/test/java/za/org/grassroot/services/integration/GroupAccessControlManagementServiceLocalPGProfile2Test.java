package za.org.grassroot.services.integration;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.services.RoleManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.acl.GroupAccessControlManagementService;

/**
 * Test Case For Local PG Only. Ignored By Default
 *
 * @author Lesetse Kimwaga
 */
@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class})
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.LOCAL_PG)
public class GroupAccessControlManagementServiceLocalPGProfile2Test {

    @Autowired
    private GroupAccessControlManagementService groupAccessControlManagementService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Test
    public void testAssignGroupRoles() throws Exception {
        /* User user = userManagementService.getAllUsers().get(0);//

        assertThat(user.getUsername(), is(notNullValue()));

        Group group = new Group("Reds", user);
        group = groupManagementService.saveGroup(group,false,"",0L);


        Role groupRole = new Role(BaseRoles.ROLE_GROUP_ORGANIZER, group.getUid());
        groupRole.setPermissions(ImmutableSet.copyOf(Permission.values()));

        groupRole = roleManagementService.createRole(groupRole);

        assertThat(groupRole.getPermissions(), is(not(empty())));*/


        //***************************************************
        // Assign Permission to User
        //***************************************************

         /*groupAccessControlManagementService.addUserGroupPermissions(group, user, groupRole.getPermissions());

         Permission somePermission = Permission.values() [0];

         assertTrue(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));*/


        //***************************************************
        // Remove Assigned Permission From User
        //***************************************************
        /*groupAccessControlManagementService.removeUserGroupPermissions(group, user, groupRole.getPermissions());

        assertFalse(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));*/
    }


}
