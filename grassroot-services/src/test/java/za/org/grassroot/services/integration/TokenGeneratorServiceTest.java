package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootCoreConfig;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.services.util.TokenGeneratorManager;
import za.org.grassroot.services.util.TokenGeneratorService;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassRootServicesConfig.class, TestContextConfig.class})
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class TokenGeneratorServiceTest {

    private Logger log = LoggerFactory.getLogger(TokenGeneratorServiceTest.class);


    @Autowired
    private TokenGeneratorService generatorService;

    @Test
    @Repeat(15)
    public void testGetNextToken() throws Exception {

        String token = generatorService.getNextToken();
        log.info("Generated Token: {}",token);
    }
}
