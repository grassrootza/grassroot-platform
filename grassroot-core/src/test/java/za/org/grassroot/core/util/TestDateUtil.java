package za.org.grassroot.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Created by aakilomar on 10/25/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class TestDateUtil {
    @Test
    public void shouldReturn1440() {
        assertEquals(1440, DateTimeUtil.numberOfMinutesForDays(1));
    }

    @Test
    public void shouldReturnMinus2880() {
        assertEquals(-2880, DateTimeUtil.numberOfMinutesForDays(-2));
    }

}





