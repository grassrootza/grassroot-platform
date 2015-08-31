package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.GrassRootCoreConfig;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.services.PasswordTokenManager;
import za.org.grassroot.services.UserManagementService;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class, GrassRootCoreConfig.class})
@Transactional
public class PasswordTokenManagerTest {

    private Logger log = LoggerFactory.getLogger(PasswordTokenManagerTest.class);


    @Autowired
    private PasswordTokenManager passwordTokenManager;
    @Autowired
    private UserManagementService userManagementService;

    @Test
    @Repeat(5)
    public void testName() throws Exception {

        User user = userManagementService.createUserProfile(new User("27700000"));

        VerificationTokenCode verificationTokenCode = passwordTokenManager.generateVerificationCode(user);

        log.info("Generated Code: {}",verificationTokenCode);
    }
}
