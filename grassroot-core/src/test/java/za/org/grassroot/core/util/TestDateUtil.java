package za.org.grassroot.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

/**
 * Created by Siyanda Mzam on 2016/04/07 13:24
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





