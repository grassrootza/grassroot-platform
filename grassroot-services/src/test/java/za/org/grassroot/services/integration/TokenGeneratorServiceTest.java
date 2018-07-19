package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.services.ServicesTestConfig;
import za.org.grassroot.services.util.TokenGeneratorService;

/**
 * @author Lesetse Kimwaga
 */
@RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = ServicesTestConfig.class)
public class TokenGeneratorServiceTest {

    private Logger log = LoggerFactory.getLogger(TokenGeneratorServiceTest.class);

    @Autowired
    private TokenGeneratorService generatorService;

    @Test @Repeat(15)
    public void testGetNextToken() throws Exception {

        String token = generatorService.getNextToken();
        log.info("Generated Token: {}",token);
    }
}
