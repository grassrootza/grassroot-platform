package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassrootServicesConfig;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.services.util.TokenGeneratorService;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {GrassrootServicesConfig.class, TestContextConfig.class})
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
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
