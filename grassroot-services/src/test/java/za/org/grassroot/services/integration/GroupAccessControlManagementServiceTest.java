package za.org.grassroot.services.integration;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.hamcrest.Matchers;
import org.junit.Before;
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
import za.org.grassroot.core.security.CustomPermissionFactory;
import za.org.grassroot.services.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class})
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GroupAccessControlManagementServiceTest {

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


    @Autowired
    private CustomPermissionFactory customPermissionFactory;


    @Before
    public void setUp() throws Exception {

        Permission permission1 = new Permission(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 2);
        permission1 = permissionsManagementService.createPermission(permission1);

        Permission permission2 = new Permission(BasePermissions.GROUP_PERMISSION_AUTHORIZE_SUBGROUP, 4);
        permission2 = permissionsManagementService.createPermission(permission2);

        Permission permission3 = new Permission(BasePermissions.GROUP_PERMISSION_DELETE_GROUP_MEMBER, 8);
        permission3 = permissionsManagementService.createPermission(permission3);

        Permission permission4 = new Permission(BasePermissions.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE, 16);
        permission4 = permissionsManagementService.createPermission(permission4);

        customPermissionFactory.registerPermissions( Lists.newArrayList(permission1, permission2, permission3, permission4));

    }

    @Test
    public void testAssignGroupRoles() throws Exception {

        User user = userManagementService.loadOrSaveUser("27720000123");
        assertThat(user.getUsername(), Matchers.notNullValue());

        Group group = new Group("Reds", user);
        group = groupManagementService.saveGroup(group,false,"",0L);


        Role groupRole = new Role(BaseRoles.ROLE_GROUP_ORGANIZER, group.getId(), group.getGroupName());

        groupRole.setPermissions(ImmutableSet.copyOf(permissionsManagementService.getPermissions()));

        groupRole = roleManagementService.createRole(groupRole);
        assertThat(groupRole.getPermissions(), hasSize(4));


        groupAccessControlManagementService.addUserGroupPermissions(group, user, groupRole.getPermissions());

        Permission somePermission = Iterables.getFirst(permissionsManagementService.getPermissions(), null);

        assertTrue(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));

        groupAccessControlManagementService.removeUserGroupPermissions(group, user, groupRole.getPermissions());

        assertFalse(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));

        //Add Permissions again to verify what happens
        groupAccessControlManagementService.addUserGroupPermissions(group, user, groupRole.getPermissions());
    }




}
