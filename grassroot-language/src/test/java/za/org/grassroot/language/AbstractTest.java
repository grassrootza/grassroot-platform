package za.org.grassroot.language;

import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Joe Stelmach
 */
public abstract class AbstractTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractTest.class);

    private static Calendar _calendar;
    protected static CalendarSource calendarSource;
    protected static Parser _parser;


    public static void initCalendarAndParser() {
        _calendar = Calendar.getInstance();
        _parser = new Parser();
    }

    /**
     * Resets the calendar source time before each test
     */
    @Before
    public void before() {
        calendarSource = new CalendarSource();
    }

    /**
     * Parses the given value into a collection of dates
     *
     * @param value
     * @return
     */
    protected List<Date> parseCollection(Date referenceDate, String value) {
        log.debug("Inside the AbstractTest Class...about to parse this={}, with this ref date={}", value, referenceDate);
        List<DateGroup> dateGroup = _parser.parse(value, referenceDate);
        log.debug("Just parsed it... Got a list of date groups with size " + dateGroup.size());
        return dateGroup.isEmpty() ? new ArrayList<Date>() : dateGroup.get(0).getDates();
    }

    /**
     * Parses the given value, asserting that one and only one date is produced.
     *
     * @param value
     * @return
     */
    protected Date parseSingleDate(String value, Date referenceDate) {
        log.debug("Inside parsingSingleDate..., about to thoroughly examine {} for \"dates\"... with refDate", value, referenceDate);
        List<Date> dates =  parseCollection(referenceDate, value);
        log.debug("Done!!! Works of a beautiful parser look like this -> " + dates);
        assertEquals(1, dates.size());
        return dates.get(0);
    }

    /**
     * Asserts that the given string value parses down to the given
     * month, day, and year values.
     *
     * @param value
     * @param month
     * @param day
     * @param year
     */
    protected void validateDate(Date referenceDate, String value, int day, int month, int year) {
        Date date = parseSingleDate(value, referenceDate);
        validateDate(date, month, day, year);
    }

    protected void validateDate(String value, int day, int month, int year) {
        validateDate(new Date(), value, day, month, year);
    }

    /**
     * Asserts that the given date contains the given attributes
     *
     * @param date
     * @param month
     * @param day
     * @param year
     */
    protected void validateDate(Date date, int day, int month, int year) {
        _calendar.setTime(date);
        assertEquals(month - 1, _calendar.get(Calendar.MONTH));
        assertEquals(day, _calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(year, _calendar.get(Calendar.YEAR));
    }

    /**
     * Asserts that the given string value parses down to the given
     * hours, minutes, and seconds
     *
     * @param value
     * @param hours
     * @param minutes
     * @param seconds
     */
    protected void validateTime(Date referenceDate, String value, int hours, int minutes, int seconds) {
        Date date = parseSingleDate(value, referenceDate);
        validateTime(date, hours, minutes, seconds);
    }

    /**
     * Asserts that the given date contains the given time attributes
     *
     * @param date
     * @param hours
     * @param minutes
     * @param seconds
     */
    protected void validateTime(Date date, int hours, int minutes, int seconds) {
        _calendar.setTime(date);
        assertEquals(hours, _calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(minutes, _calendar.get(Calendar.MINUTE));
        assertEquals(seconds, _calendar.get(Calendar.SECOND));
    }

    /**
     * Asserts that the given string value parses down to the given
     * month, day, year, hours, minutes, and seconds
     *
     * @param value
     * @param month
     * @param day
     * @param year
     * @param hours
     * @param minutes
     * @param seconds
     */
    protected void validateDateTime(Date referenceDate, String value, int day, int month, int year,
                                    int hours, int minutes, int seconds) {

        log.debug("Trying to parse string={}, with refDate={}", value, referenceDate);

        Date date = parseSingleDate(value, referenceDate);
        log.debug("Tried to parse and now I got back this: " + date.toString());
        validateDateTime(date, day, month, year, hours, minutes, seconds);
    }

    protected void validateDateTimeUS(Date referenceDate, String value, int month, int day, int year,
                                      int hours, int minutes, int seconds) {
        validateDateTime(referenceDate, value, day, month, year, hours, minutes, seconds);
    }

    /**
     * Asserts that the given date contains the given attributes
     *
     * @param date
     * @param month
     * @param day
     * @param year
     * @param hours
     * @param minutes
     * @param seconds
     */
    protected void validateDateTime(Date date, int day, int month, int year,
                                    int hours, int minutes, int seconds) {

        log.debug(String.format("Date passed, back as: %s, expecting month number %d and day number %d",
                date.toString(), month, day));

        _calendar.setTime(date);
        assertEquals(month - 1, _calendar.get(Calendar.MONTH));
        assertEquals(day, _calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(year, _calendar.get(Calendar.YEAR));
        assertEquals(hours, _calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(minutes, _calendar.get(Calendar.MINUTE));
        assertEquals(seconds, _calendar.get(Calendar.SECOND));
    }

    protected void validateDateTimeUS(Date date, int month, int day, int year,
                                      int hours, int minutes, int seconds) {
        validateDateTime(date, day, month, year, hours, minutes, seconds);
    }
}
