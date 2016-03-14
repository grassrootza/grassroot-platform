package za.org.grassroot.services.integration;

import com.google.common.collect.ImmutableSet;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.acl.GroupAccessControlManagementService;
import za.org.grassroot.services.RoleManagementService;
import za.org.grassroot.services.UserManagementService;

import static org.hamcrest.Matchers.hasSize;
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
    private GroupRepository groupRepository;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Test
    public void testAssignGroupRoles() throws Exception {
        /* User user = userManagementService.loadOrSaveUser("27720000123");
        assertThat(user.getUsername(), Matchers.notNullValue());

        Group group = new Group("Reds", user);
        group = groupRepository.save(group);

        Role groupRole = new Role(BaseRoles.ROLE_GROUP_ORGANIZER, group.getUid());

        groupRole.setPermissions(ImmutableSet.copyOf(Permission.values()));

        groupRole = roleManagementService.createRole(groupRole);
        assertThat(groupRole.getPermissions(), hasSize(30));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            authentication = new UsernamePasswordAuthenticationToken(user,
                    user.getPassword(), user.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }


        groupAccessControlManagementService.addUserGroupPermissions(group, user, groupRole.getPermissions());

        Permission somePermission = Permission.values() [0];

        assertTrue(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));

        groupAccessControlManagementService.removeUserGroupPermissions(group, user, groupRole.getPermissions());

        assertFalse(groupAccessControlManagementService.hasGroupPermission(somePermission, group, user));

        //Add Permissions again to verify what happens
        groupAccessControlManagementService.addUserGroupPermissions(group, user, groupRole.getPermissions());*/
    }
}
