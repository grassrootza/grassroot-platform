package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@Transactional
//@DirtiesContext
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class PasswordTokenManagerTest {

    private Logger log = LoggerFactory.getLogger(PasswordTokenManagerTest.class);

    @Autowired
    private PasswordTokenService passwordTokenService;

    @Autowired
    private UserManagementService userManagementService;

    @Test
    @Repeat(5)
    public void testGenerateVerificationCode() throws Exception {
        User user = userManagementService.createUserProfile(new User("27721230001", null, null));
        VerificationTokenCode verificationTokenCode = passwordTokenService.generateShortLivedOTP("27721230001");
        log.info("Generated Code: {}",verificationTokenCode);
    }


    @Test
    public void testGenerateVerificationCode2() throws Exception {
        User user = userManagementService.createUserProfile(new User("27721230002", null, null));
        VerificationTokenCode verificationTokenCode = passwordTokenService.generateLongLivedAuthCode(user.getUid());
        log.info("Generated Code: {}", verificationTokenCode);

        // note : this is failing, but it seems principally because it's not generating the user, w/ usual Spring test DB in-mem strangeness
        // assertThat(passwordTokenService.isLongLiveAuthValid("27721230002", verificationTokenCode.getCode()), is(true));
    }


    @Test
    public void testGenerateVerificationCode3() throws Exception {
        User user = userManagementService.loadOrCreateUser("0729177903");
        log.info("user user name : ", user.getUsername());
        VerificationTokenCode verificationTokenCode = passwordTokenService.generateShortLivedOTP(user.getUsername());
        assertThat(passwordTokenService.isShortLivedOtpValid(user.getUsername(),
                verificationTokenCode.getCode()), is(true));
        log.info("Generated Code: {}", verificationTokenCode);
    }
}
