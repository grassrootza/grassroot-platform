package za.org.grassroot.webapp.controller.webapp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.GrassrootWebApplicationConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {GrassrootWebApplicationConfig.class})
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class UserProfileControllerIT {

    @Autowired
    private UserManagementService userManagementService;

    @Test
    public void testName() throws Exception {
        User userProfile = new User("1201994", "Grass Root");
        userProfile = userManagementService.createUserProfile(userProfile);

        assertThat(userProfile.getDisplayName(), equalTo("Grass Root"));
    }
}
