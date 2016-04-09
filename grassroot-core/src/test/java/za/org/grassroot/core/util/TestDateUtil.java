package za.org.grassroot.core.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

import static org.junit.Assert.assertEquals;

/**
 * Created by Siyanda Mzam on 2016/04/07 13:24 AM.
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

    /**
     * Tests for explicit date_time with a (two-, four-, or zero-digit) year specified
     * and a time hinted by an "@" sign or "at | AT"
     * Also tests for time that has varying delimiters and that which has no delimiters and has no time hinting characters
     **/
    @Test
    public void parsingAbsoluteDateTimeShouldWork() throws Exception {

        LocalDateTime referenceDateTime = LocalDateTime.of(2016, 06, 25, 17, 0);

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 @ 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 @17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-2016@ 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016@17h00"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 at 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 at17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-2016at 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016at17h00"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 AT 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 AT17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016AT 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016AT17h00"));


        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 @ 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 @17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-16@ 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16@17h00"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 at 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 at17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-16at 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16at17h00"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 AT 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 AT17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-16AT 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16AT17h00"));


        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06 @ 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06 @17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06@ 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06@17h00"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 AT 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 AT17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06AT 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06AT17h00"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 at 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 at17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06at 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06at17h00"));


        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 17H00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-2016 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/2016 17.00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-2016 17"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 17H00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-16 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06/16 1700"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06-16 17"));

        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 17h00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 17H00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25/06 17:00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 17.00"));
        assertEquals(referenceDateTime, DateTimeUtil.parseDateTime("25-06 17"));
    }

    /**
     * Tests for a relative date_time with similar properties as the tests above.
     */
    @Test
    public void parsingRelativeDateTimeShouldWork() throws Exception {

        final LocalDateTime referenceDateTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(00, 00)).truncatedTo(ChronoUnit.SECONDS);

        assertEquals(referenceDateTime.plusHours(19), DateTimeUtil.parseDateTime("today at 7 in the evening").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.minusDays(1).plusHours(9), DateTimeUtil.parseDateTime("yesterday at 9 in the morning").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.minusDays(2).plusHours(18), DateTimeUtil.parseDateTime("the day before yesterday at 6 in the evening ").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.plusDays(2).plusHours(16).plusMinutes(30), DateTimeUtil.parseDateTime("at 4h30pm the day after tomorrow ").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).plusWeeks(3).plusHours(17), DateTimeUtil.parseDateTime("mon after two weeks at 1700").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.plusDays(4).plusHours(10), DateTimeUtil.parseDateTime("four days from now @ 10 am").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.with(Month.SEPTEMBER).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.WEDNESDAY)).plusHours(15).plusMinutes(35), DateTimeUtil.parseDateTime("2nd wednesday of sept at 1535").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.with(Month.DECEMBER).withDayOfMonth(24).withHour(14).withMinute(3), DateTimeUtil.parseDateTime("meeting will be held on christmas eve at 2:03pm").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.with(Month.JANUARY).withDayOfYear(1).plusYears(1).plusMonths(3).plusDays(15).plusHours(13).plusMinutes(40), DateTimeUtil.parseDateTime("at 13h40 on easter monday").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.plusMonths(1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.THURSDAY)).plusHours(13).plusMinutes(23), DateTimeUtil.parseDateTime("third thursday of next month at 13H23").truncatedTo(ChronoUnit.SECONDS));

        // TODO: 2016/04/08 Get antlr to recognise the first occurrence of particular dates like, "fri the 13th" or "first/next holiday of this month/year".
    }
}





