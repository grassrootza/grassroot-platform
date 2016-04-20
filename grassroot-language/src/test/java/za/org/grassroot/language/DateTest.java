package za.org.grassroot.language;

import org.apache.log4j.Level;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.Map.Entry;

/**
 * Runs the parser through the various date formats
 *
 * @author Joe Stelmach
 */
public class DateTest extends AbstractTest {

    @BeforeClass
    public static void oneTime() {
        Locale.setDefault(Locale.getDefault());
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg"));
        initCalendarAndParser();
    }

    @Test
    public void testFormal() {
        validateDate("28-01-1978", 28, 1, 1978);
        validateDate("10-10-2009", 10, 10, 2009);
        validateDate("1-2-1980", 1, 2, 1980);
        validateDate("12/12/12", 12, 12, 2012);
        validateDate("3/4", 3, 4, Calendar.getInstance().get(Calendar.YEAR));
        validateDate("sun, 21/11/2010", 21, 11, 2010);
        //validateDate("october 2006", 1, 10, 2006);
        //validateDate("feb 1979", 1, 2, 1979);
        //validateDate("jan '80", 1, 1, 1980);
        validateDate("Jun-16 2016", 16, 6, 2016);
        validateDate("28-Feb-2010", 28, 2, 2010);
        validateDate("9-Apr", 9, 4, Calendar.getInstance().get(Calendar.YEAR));
        validateDate("jan 10, '00", 10, 1, 2000);
    }

    @Test
    public void testRelaxed() {
        validateDate("oct 1, 1980", 1, 10, 1980);
        validateDate("oct. 1, 1980", 1, 10, 1980);
        validateDate("oct 1,1980", 1, 10, 1980);
        validateDate("1st oct in the year '89", 1, 10, 1989);
        validateDate("thirty first of december '80", 31, 12, 1980);
        validateDate("the first of december in the year 1980", 1, 12, 1980);
        validateDate("the 2 of february in the year 1980", 2, 2, 1980);
        validateDate("the 2nd of february in the year 1980", 2, 2, 1980);
        validateDate("the second of february in the year 1980", 2, 2, 1980);
        validateDate("jan. 2nd", 2, 1, Calendar.getInstance().get(Calendar.YEAR));
        validateDate("sun, nov 21 2010", 21, 11, 2010);
        //validateDate("Second Monday in October 2017", 9, 10, 2017);
        //validateDate("2nd thursday in sept. '02", 12, 9, 2002);
    }

    @Test
    public void testExplicitRelative() throws Exception {
        Date reference = sdfNoTime.parse("28/2/2011");
        calendarSource = new CalendarSource(reference);

        // validateDate(reference, "last thursday in april", 28, 4, 2011);
        // validateDate(reference, "final thurs in sep", 29, 9, 2011);
        validateDate(reference, "4th february ", 4, 2, 2011);
    }

    @Test
    public void testRelative() throws Exception {
        Date reference = sdfNoTime.parse("28/2/2011");
        calendarSource = new CalendarSource(reference);

        validateDate(reference, "yesterday", 27, 2, 2011);
        validateDate(reference, "tomorrow", 1, 3, 2011);
        validateDate(reference, "tmr", 1, 3, 2011);
        validateDate(reference, "in 3 days", 3, 3, 2011);
        validateDate(reference, "3 days ago", 25, 2, 2011);
        validateDate(reference, "in 3 weeks", 21, 3, 2011);
        validateDate(reference, "four weeks ago", 31, 1, 2011);
        validateDate(reference, "in 3 months", 28, 5, 2011);
        validateDate(reference, "three months ago", 28, 11, 2010);
        validateDate(reference, "in 3 years", 28, 2, 2014);
        validateDate(reference, "seven years ago", 28, 2, 2004);
        validateDate(reference, "60 years ago", 28, 2, 1951);
        validateDate(reference, "32 days ago", 27, 1, 2011);
        validateDate(reference, "320 days ago", 14, 4, 2010);
        validateDate(reference, "1200 days ago", 16, 11, 2007);
        validateDate(reference, "365 days from now", 28, 2, 2012);
        validateDate(reference, "100 months from now", 28, 6, 2019);
        validateDate(reference, "next monday", 7, 3, 2011);
        validateDate(reference, "4 mondays from now", 28, 3, 2011);
        validateDate(reference, "4 mondays from today", 28, 3, 2011);
        validateDate(reference, "next weekend", 12, 3, 2011);
        validateDate(reference, "six mondays ago", 17, 1, 2011);
        validateDate(reference, "last monday", 21, 2, 2011);
        validateDate(reference, "last mon", 21, 2, 2011);
        validateDate(reference, "this past mon", 21, 2, 2011);
        validateDate(reference, "this coming mon", 7, 3, 2011);
        validateDate(reference, "this upcoming mon", 7, 3, 2011);
        validateDate(reference, "next thurs", 10, 3, 2011);
        validateDate(reference, "next month", 28, 3, 2011);
        validateDate(reference, "last month", 28, 1, 2011);
        validateDate(reference, "next week", 7, 3, 2011);
        validateDate(reference, "last week", 21, 2, 2011);
        validateDate(reference, "next year", 28, 2, 2012);
        validateDate(reference, "last year", 28, 2, 2010);
        validateDate(reference, "tues this week", 1, 3, 2011);
        validateDate(reference, "tuesday this week", 1, 3, 2011);
        validateDate(reference, "tuesday next week", 8, 3, 2011);
        validateDate(reference, "this september", 1, 9, 2011);
        validateDate(reference, "in a september", 1, 9, 2011);
        validateDate(reference, "in an october", 1, 10, 2011);
        validateDate(reference, "september", 1, 9, 2011);
        validateDate(reference, "last september", 1, 9, 2010);
        validateDate(reference, "next september", 1, 9, 2011);
        validateDate(reference, "january", 1, 1, 2011);
        validateDate(reference, "last january", 1, 1, 2011);
        validateDate(reference, "next january", 1, 1, 2012);
        validateDate(reference, "next february", 1, 2, 2012);
        validateDate(reference, "last february", 1, 2, 2010);
        validateDate(reference, "february", 1, 2, 2011);
        validateDate(reference, "in a year", 28, 2, 2012);
        validateDate(reference, "in a week", 7, 3, 2011);
        validateDate(reference, "the saturday after next", 19, 3, 2011);
        validateDate(reference, "the monday after next", 14, 3, 2011);
        validateDate(reference, "the monday after next monday", 14, 3, 2011);
        validateDate(reference, "the monday before May 25", 23, 5, 2011);
        validateDate(reference, "3 mondays after May 25", 13, 6, 2011);
        validateDate(reference, "tuesday before last", 15, 2, 2011);
        validateDate(reference, "a week from now", 7, 3, 2011);
        validateDate(reference, "a month from today", 28, 3, 2011);
        validateDate(reference, "a week after this friday", 11, 3, 2011);
        validateDate(reference, "a week from this friday", 11, 3, 2011);
        validateDate(reference, "two weeks from this friday", 18, 3, 2011);
        validateDate(reference, "A week on tuesday", 8, 3, 2011);
        validateDate(reference, "A month ago", 28, 1, 2011);
        validateDate(reference, "A week ago", 21, 2, 2011);
        validateDate(reference, "A year ago", 28, 2, 2010);
        validateDate(reference, "this month", 28, 2, 2011);
        validateDate(reference, "current month", 28, 2, 2011);
        validateDate(reference, "current year", 28, 2, 2011);
        validateDate(reference, "1 year 9 months from now", 28, 11, 2012);
        validateDate(reference, "1 year 9 months 1 day from now", 29, 11, 2012);
        validateDate(reference, "2 years 4 months ago", 28, 10, 2008);
        validateDate(reference, "2 years 4 months 5 days ago", 23, 10, 2008);
        validateDate(reference, "next mon", 7, 3, 2011);

      /*
        validateDate(reference, "100 years from now", 28, 2, 2111);
        validateDate(reference, "the second week after this friday", 18, 3, 2011);
        validateDate(reference, "first monday in 1 month", 7, 3, 2011);
        validateDate(reference, "first monday of month in 1 month", 7, 3, 2011);
        validateDate(reference, "first monday of 1 month", 7, 3, 2011);
        validateDate(reference, "first monday in 2 months", 4, 4, 2011);
        validateDate(reference, "first monday of 2 months", 4, 4, 2011);
        validateDate(reference, "first monday of month 2 months", 4, 4, 2011);
        validateDate(reference, "first monday of month in 2 months", 4, 4, 2011);
        validateDate(reference, "first monday in 3 months", 2, 5, 2011);
        validateDate(reference, "first monday of 3 months", 2, 5, 2011);
        validateDate(reference, "first monday of month in 3 months", 2, 5, 2011);
        validateDate(reference, "the 2nd monday before May 25", 16, 5, 2011);
        validateDate(reference, "It's gonna snow! How about skiing tomorrow", 1, 3, 2011);
        */
    }

    @Test
    public void testRange() throws Exception {
        Date reference = sdfNoTime.parse("02/1/2011");
        calendarSource = new CalendarSource(reference);

        List<Date> dates = parseCollection(reference, "monday to friday");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 3, 1, 2011);
        validateDate(dates.get(1), 7, 1, 2011);

        dates = parseCollection(reference, "may 2nd to 5th");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 5, 2011);
        validateDate(dates.get(1), 5, 5, 2011);

        dates = parseCollection(reference, "1/3 to 2/3");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 3, 2011);
        validateDate(dates.get(1), 2, 3, 2011);

        dates = parseCollection(reference, "2/3 to in one week");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 3, 2011);
        validateDate(dates.get(1), 9, 1, 2011);

        dates = parseCollection(reference, "feb 28th or 2 days after");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 28, 2, 2011);
        validateDate(dates.get(1), 2, 3, 2011);

        dates = parseCollection(reference, "tomorrow at 10 and monday at 11");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 3, 1, 2011);
        validateDate(dates.get(1), 3, 1, 2011);

        dates = parseCollection(reference, "tomorrow at 10 through tues at 11");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 3, 1, 2011);
        validateDate(dates.get(1), 4, 1, 2011);

        dates = parseCollection(reference, "first day of 2009 to last day of 2009");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 1, 2009);
        validateDate(dates.get(1), 31, 12, 2009);

        dates = parseCollection(reference, "first to last day of 2008");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 1, 2008);
        validateDate(dates.get(1), 31, 12, 2008);

        dates = parseCollection(reference, "first to last day of september");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 9, 2011);
        validateDate(dates.get(1), 30, 9, 2011);

        dates = parseCollection(reference, "first to last day of this september");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 9, 2011);
        validateDate(dates.get(1), 30, 9, 2011);

        dates = parseCollection(reference, "first to last day of 2 septembers ago");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 9, 2009);
        validateDate(dates.get(1), 30, 9, 2009);

        dates = parseCollection(reference, "first to last day of 2 septembers from now");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 9, 2012);
        validateDate(dates.get(1), 30, 9, 2012);

        dates = parseCollection(reference, "for 5 days");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 7, 1, 2011);

        dates = parseCollection(reference, "for ten months");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 11, 2011);

        dates = parseCollection(reference, "for twenty-five years");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 1, 2036);

        dates = parseCollection(reference, "2 and 4 months");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 3, 2011);
        validateDate(dates.get(1), 2, 5, 2011);

        dates = parseCollection(reference, "in 2 to 4 months");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 3, 2011);
        validateDate(dates.get(1), 2, 5, 2011);

        dates = parseCollection(reference, "for 2 to 4 months");
        Assert.assertEquals(3, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 3, 2011);
        validateDate(dates.get(2), 2, 5, 2011);

        dates = parseCollection(reference, "next 2 to 4 months");
        Assert.assertEquals(3, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 3, 2011);
        validateDate(dates.get(2), 2, 5, 2011);

        dates = parseCollection(reference, "2 to 4 months from now");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 3, 2011);
        validateDate(dates.get(1), 2, 5, 2011);

        dates = parseCollection(reference, "last 2 to 4 months");
        Assert.assertEquals(3, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 11, 2010);
        validateDate(dates.get(2), 2, 9, 2010);

        dates = parseCollection(reference, "past 2 to 4 months");
        Assert.assertEquals(3, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 11, 2010);
        validateDate(dates.get(2), 2, 9, 2010);

        dates = parseCollection(reference, "2 to 4 months ago");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 11, 2010);
        validateDate(dates.get(1), 2, 9, 2010);

        dates = parseCollection(reference, "2 or 3 days ago");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 31, 12, 2010);
        validateDate(dates.get(1), 30, 12, 2010);

        dates = parseCollection(reference, "1 to 2 days");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 3, 1, 2011);
        validateDate(dates.get(1), 4, 1, 2011);

        dates = parseCollection(reference, "bla bla bla 2 and 4 month");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 3, 2011);
        validateDate(dates.get(1), 2, 5, 2011);

        /*dates = parseCollection(reference, "1999-12-31 to tomorrow");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 31, 12, 1999);
        validateDate(dates.get(1), 3, 1, 2011);

        dates = parseCollection(reference, "now to 2010-01-01");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 1, 1, 2010);

        dates = parseCollection(reference, "jan 1 to 2");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 1, 2011);
        validateDate(dates.get(1), 2, 1, 2011);

        dates = parseCollection(reference, "first day of may to last day of may");
        Assert.assertEquals(2, dates.size());
        validateDate(dates.get(0), 1, 5, 2011);
        validateDate(dates.get(1), 31, 5, 2011);

        dates = parseCollection(reference, "I want to go shopping in Knoxville, TN in the next five to six months.");
        Assert.assertEquals(3, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 6, 2011);
        validateDate(dates.get(2), 2, 7, 2011);

        dates = parseCollection(reference, "I want to watch the fireworks in the next two to three months.");
        Assert.assertEquals(3, dates.size());
        validateDate(dates.get(0), 2, 1, 2011);
        validateDate(dates.get(1), 2, 3, 2011);
        validateDate(dates.get(2), 2, 4, 2011);

        dates = parseCollection(reference, "september 7th something");
        Assert.assertEquals(1, dates.size());
        validateDate(dates.get(0), 7, 9, 2011);

        dates = parseCollection(reference, "september 7th something happened here");
        Assert.assertEquals(1, dates.size());
        validateDate(dates.get(0), 7, 9, 2011);
*/

           }

    // https://github.za.org.grassroot.language/issues/38
    @Test
    public void testRelativeDateDifferentTimezone() {
        // Prepare
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg"));
        Parser parser = new Parser(TimeZone.getTimeZone("US/Pacific"));
        // 2012, June 3, Sunday, 1 a.m. in US/Eastern GMT -4
        // Same time as
        // 2012, June 2, Saturday, 10 p.m. in US/Pacific GMT -7
        Calendar earlySunday = new GregorianCalendar(2012, 5, 3, 1, 0);
        calendarSource = new CalendarSource(earlySunday.getTime());

        // Run
        Date result = parser.parse("Sunday at 10am", earlySunday.getTime()).get(0).getDates().get(0);

        // Validate
        // Result should be June 3, 2012
        validateDate(result, 3, 6, 2012);

        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg"));
    }

    public static void main(String[] args) {

        String value = "easter '06";

        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);

        Parser parser = new Parser();
        List<DateGroup> groups = parser.parse(value);
        for (DateGroup group : groups) {
            System.out.println(value);
            System.out.println(group.getSyntaxTree().toStringTree());
            System.out.println("line: " + group.getLine() + ", column: " + group.getPosition());
            System.out.println(group.getText());
            System.out.println(group.getDates());
            System.out.println("is time inferred: " + group.isTimeInferred());
            System.out.println("is recurring: " + group.isRecurring());
            System.out.println("recurs until: " + group.getRecursUntil());

            System.out.println("\n** Parse Locations **");
            for (Entry<String, List<ParseLocation>> entry : group.getParseLocations().entrySet()) {
                for (ParseLocation loc : entry.getValue()) {
                    System.out.println(loc.getRuleName() + ": " + loc.getText());
                }
            }

            List<ParseLocation> conjunctionLocations = group.getParseLocations().get("conjunction");
            if (conjunctionLocations != null) {
                System.out.print("\nconjunctions: ");
                for (ParseLocation location : conjunctionLocations) {
                    System.out.print(location.getText() + " ");
                }
            }
            System.out.print("\n\n");
        }
    }
}
