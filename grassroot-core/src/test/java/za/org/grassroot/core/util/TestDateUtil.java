package za.org.grassroot.core.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;

import static org.junit.Assert.assertEquals;

/**
 * Created by Siyanda Mzam on 2016/04/07 13:24
 */
@Slf4j @RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = TestContextConfiguration.class)
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





