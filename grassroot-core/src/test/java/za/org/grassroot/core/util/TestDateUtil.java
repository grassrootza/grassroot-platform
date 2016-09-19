package za.org.grassroot.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;

import static org.junit.Assert.assertEquals;

/**
 * Created by Siyanda Mzam on 2016/04/07 13:24
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
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





