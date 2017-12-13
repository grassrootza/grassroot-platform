package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.user.UserManagementService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class UserManagementServiceTest {

    @Autowired
    private UserManagementService userManagementService;

    @Test
    public void testName() throws Exception {
        User userProfile = new User("1201994", "Grass Root", null);
        userProfile = userManagementService.createUserProfile(userProfile);
        assertThat(userProfile.getDisplayName(), equalTo("Grass Root"));
    }

    @Test
    public void shouldLoadOrSave() {
        User user = userManagementService.loadOrCreateUser("0826607135");
        Assert.assertNotEquals(Long.parseLong("0"),Long.parseLong(user.getId().toString()));
    }
}
