package za.org.grassroot.services.integration;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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
    private GroupManagementService groupManagementService;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private PermissionsManagementService permissionsManagementService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Test
    public void testAssignGroupRoles() throws Exception {

        assertThat(permissionsManagementService.getPermissions(), is(not(empty())));

        User user = userManagementService.getAllUsers().get(0);//

        assertThat(user.getUsername(), is(notNullValue()));

        Group group = new Group("Reds", user);
        group = groupManagementService.saveGroup(group,false,"",0L);


        Role groupRole = new Role(BaseRoles.ROLE_GROUP_ORGANIZER, group.getId(), group.getGroupName());
        groupRole.setPermissions(ImmutableSet.copyOf(permissionsManagementService.getPermissions()));

        groupRole = roleManagementService.createRole(groupRole);

        assertThat(groupRole.getPermissions(), is(not(empty())));


        //***************************************************
        // Assign Permission to User
        //***************************************************

         groupAccessControlManagementService.addUserGroupPermissions(group, user, groupRole.getPermissions());

         Permission somePermission = Iterables.getFirst(permissionsManagementService.getPermissions(), null);

         assertTrue(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));


        //***************************************************
        // Remove Assigned Permission From User
        //***************************************************
        groupAccessControlManagementService.removeUserGroupPermissions(group, user, groupRole.getPermissions());

        assertFalse(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));
    }


}
