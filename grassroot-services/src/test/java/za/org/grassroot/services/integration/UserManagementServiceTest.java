package za.org.grassroot.services.integration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManagementService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@EnableTransactionManagement
public class UserManagementServiceTest {

    @Autowired
    private UserManagementService userManagementService;

    @Test
    public void testName() throws Exception {

        User userProfile = new User();
        userProfile.setDisplayName("Grass Root");
        userProfile.setPhoneNumber("1201994");

        userProfile = userManagementService.createUserProfile(userProfile);

        assertThat(userProfile.getDisplayName(), equalTo("Grass Root"));


    }

    @Test
    public void shouldLoadOrSave() {
        User user = userManagementService.loadOrSaveUser("0826607135");
        Assert.assertNotEquals(Long.parseLong("0"),Long.parseLong(user.getId().toString()));
    }
}
