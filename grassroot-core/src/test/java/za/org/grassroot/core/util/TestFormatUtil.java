package za.org.grassroot.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;

import static org.junit.Assert.assertEquals;

/**
 * Created by aakilomar on 10/25/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class TestFormatUtil {
    @Test
    public void testFormatDouble() {

        assertEquals("123", FormatUtil.formatDoubleToString(123.000));
        assertEquals("123", FormatUtil.formatDoubleToString(123));
        assertEquals("123.1", FormatUtil.formatDoubleToString(123.100));
        assertEquals("123.12", FormatUtil.formatDoubleToString(123.1200));
        assertEquals("123.123", FormatUtil.formatDoubleToString(123.123));
        assertEquals("-123", FormatUtil.formatDoubleToString(-123));
        assertEquals("-123.1", FormatUtil.formatDoubleToString(-123.100000));

    }

}





