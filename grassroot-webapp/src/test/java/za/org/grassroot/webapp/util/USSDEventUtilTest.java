package za.org.grassroot.webapp.util;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

/**
 * Created by luke on 2016/04/20.
 * For the moment, mostly used to house tests of our variants of dates and time, and conversion to local date time
 */
public class USSDEventUtilTest {
    
    /**
     * Tests for explicit date_time with a (two-, four-, or zero-digit) year specified
     * and a time hinted by an "@" sign or "at | AT"
     * Also tests for time that has varying delimiters and that which has no delimiters and has no time hinting characters
     **/
    @Test
    public void parsingAbsoluteDateTimeShouldWork() throws Exception {

        LocalDateTime referenceDateTime = LocalDateTime.of(2016, 06, 25, 17, 0);

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 @ 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 @17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-2016@ 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016@17h00"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 at 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 at17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-2016at 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016at17h00"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 AT 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 AT17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016AT 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016AT17h00"));


        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 @ 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 @17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-16@ 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16@17h00"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 at 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 at17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-16at 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16at17h00"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 AT 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 AT17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-16AT 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16AT17h00"));


        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06 @ 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06 @17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06@ 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06@17h00"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 AT 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 AT17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06AT 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06AT17h00"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 at 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 at17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06at 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06at17h00"));


        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 17H00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-2016 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/2016 17.00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-2016 17"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 17H00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-16 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06/16 1700"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06-16 17"));

        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 17h00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 17H00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25/06 17:00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 17.00"));
        assertEquals(referenceDateTime, USSDEventUtil.parseDateTime("25-06 17"));
    }

    /**
     * Tests for a relative date_time with similar properties as the tests above.
     */
    @Test
    public void parsingRelativeDateTimeShouldWork() throws Exception {

        LocalDateTime referenceDateTime = LocalDateTime.of(LocalDate.now(), LocalTime.of(00, 00)).truncatedTo(ChronoUnit.SECONDS);
        Integer referencedDayValue = 1;
        Integer todaysReferencedValue = referenceDateTime.getDayOfWeek().getValue();

        assertEquals(referenceDateTime.plusHours(19), USSDEventUtil.parseDateTime("today at 7 in the evening").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.minusDays(1).plusHours(9),
                     USSDEventUtil.parseDateTime("yesterday at 9 in the morning").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.minusDays(2).plusHours(18),
                     USSDEventUtil.parseDateTime("the day before yesterday at 6 in the evening ").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.plusDays(2).plusHours(16).plusMinutes(30),
                     USSDEventUtil.parseDateTime("at 4h30pm the day after tomorrow ").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(todaysReferencedValue > referencedDayValue ?
                             referenceDateTime.minusDays(todaysReferencedValue - referencedDayValue).plusWeeks(3).plusHours(17) :
                             todaysReferencedValue == referencedDayValue ? referenceDateTime.plusWeeks(3).plusHours(17) :
                                     referenceDateTime.plusDays((referencedDayValue - todaysReferencedValue) ).plusWeeks(2).plusHours(17),
                     USSDEventUtil.parseDateTime("mon after two weeks at 1700").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.plusDays(4).plusHours(10),
                     USSDEventUtil.parseDateTime("four days from now @ 10 am").truncatedTo(ChronoUnit.SECONDS));
        //   assertEquals(referenceDateTime.with(Month.SEPTEMBER).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.WEDNESDAY)).plusHours(15).plusMinutes(35),
        //            USSDEventUtil.parseDateTime("2nd wednesday of sept at 1535").truncatedTo(ChronoUnit.SECONDS));
        assertEquals(referenceDateTime.with(Month.DECEMBER).withDayOfMonth(1).withHour(14).withMinute(3),
                     USSDEventUtil.parseDateTime("dec at 2:03pm").truncatedTo(ChronoUnit.SECONDS));
        // assertEquals(referenceDateTime.plusMonths(1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.THURSDAY)).plusHours(13).plusMinutes(23),
//                USSDEventUtil.parseDateTime("third thursday of next month at 13H23").truncatedTo(ChronoUnit.SECONDS));

        // TODO: 2016/04/08 Get antlr to recognise the first occurrence of particular dates like, "fri the 13th" or "first/next holiday of this month/year".
    }


}
