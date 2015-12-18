package za.org.grassroot.core.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by aakilomar on 9/19/15.
 */
public class DateTimeUtil {

    private static Logger log = Logger.getLogger("DateTimeUtil");

    // todo: replace with getters
    public static final DateTimeFormatter preferredDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    public static final DateTimeFormatter preferredTimeFormat = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter preferredDateTimeFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:MM");

    private static final String possibleTimeDelims = "[-, :]+";
    private static final Joiner timeJoiner = Joiner.on(":").skipNulls();
    private static final Pattern timeWithDelims = Pattern.compile("\\d{1,2}" + possibleTimeDelims + "\\d\\d");
    private static final Pattern timeWithoutDelims = Pattern.compile("\\d{3,4}");
    private static final Pattern timeHourOnly = Pattern.compile("\\d{1,2}[am|pm]?");
    private static final Pattern neededOutput = Pattern.compile("\\d{2}:\\d{2}");

    private static final LocalDateTime veryLongTimeAway = LocalDateTime.of(2099, 12, 31, 23, 59);

    public static LocalDateTime getVeryLongTimeAway() { return veryLongTimeAway; }
    public static Timestamp getVeryLongTimestamp() { return Timestamp.valueOf(veryLongTimeAway); }

        /*
        Inserting method to parse date time user input and, if it can be parsed, set the timestamp accordingly.
        todo: a lot of error handling and looking through the tree to make sure this is right.
        todo: come up with a more sensible default if the parsing fails, rather than current time
        todo: work on handling methods / customize the util library to handle local languages
        todo: make sure the timezone is being set properly
         */

    public static LocalDateTime parseDateTime(String passedValue) {

        LocalDateTime parsedDateTime;

        Parser parser = new Parser();
        DateGroup firstDateGroup = parser.parse(passedValue).iterator().next();
        if (firstDateGroup != null) {
            Date parsedDate = firstDateGroup.getDates().iterator().next();
            parsedDateTime = parsedDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            log.info("Date time processed: " + parsedDateTime.toString());
        } else {
            parsedDateTime = LocalDateTime.now();
        }

        return parsedDateTime;

    }

    public static LocalDateTime parsePreformattedString(String formattedValue) {
        // todo: exception handling just in case someone uses this / passes it badly
        return LocalDateTime.parse(formattedValue, preferredDateTimeFormat);
    }

    /*
    Helper method to deal with messy user input of a time string, more strict but also more likely to be accurate than
    free form parsing above. The menu prompt does give the preferred format of HH:mm, but never know, so check for a
    range of possible delimiters, and three basic patterns -- 15:30, 3:30 pm, 1530. Since there may be stuff around the
    outside, all we care about is finding a match, not the whole input matching, and then whether we have to add 12 to
    hour if 'pm' has been entered. We check for those, then give up if none work
     */

    // todo: major refactor of this
    public static String reformatTimeInput(String userResponse) {

        // note: we only need to worry about am/pm if pm is entered, and in that case add 12 to hour

        String reformattedTime;
        final String trimmedResponse = userResponse.trim().toLowerCase();
        boolean pmStringEntered = Pattern.matches(".*pm?", trimmedResponse);
        log.info("We have been passed this as our input ... " + trimmedResponse + " ... and we found pm: " + pmStringEntered);

        final Matcher matcherDelims = timeWithDelims.matcher(trimmedResponse);
        final Matcher matcherNoDelims = timeWithoutDelims.matcher(trimmedResponse);
        final Matcher matcherHours = timeHourOnly.matcher(trimmedResponse);

        if (matcherDelims.find()) {
            // todo: make even more robust, to, e.g., a delim character at front
            // todo: see if we can get rid of the list casting (iterator.next was behaving badly on first attempt)
            String digitsOnly = matcherDelims.group(0);
            log.info("Okay, the response matched the pattern XX:YY, or a variant thereof, extracted as ... " + digitsOnly);
            List<String> split = Lists.newArrayList(Splitter.on(CharMatcher.anyOf(possibleTimeDelims)).omitEmptyStrings().split(digitsOnly));
            log.info("And the split gives us ...  " + split.toString());
            int hours = Integer.parseInt(split.get(0)) + (pmStringEntered ? 12 : 0);
            reformattedTime = timeJoiner.join(new String[]{String.format("%02d", hours), split.get(1)});

        } else if (matcherNoDelims.find()) {
            log.info("Okay, no delimiter, but 3-4 digits in a row, assuming those are it ...");
            try {
                String digitSubString = matcherNoDelims.group(0);
                int hourDigits = digitSubString.length() - 2;
                int hours = Integer.parseInt(digitSubString.substring(0, hourDigits)) + (pmStringEntered ? 12 :0);
                int minutes = Integer.parseInt(digitSubString.substring(hourDigits, hourDigits + 2));
                reformattedTime = timeJoiner.join(new String[]{String.format("%02d", hours), String.format("%02d", minutes)});
                log.info("Finished up with digits and we have: " + reformattedTime);
            } catch (NumberFormatException e) {
                log.info("Didn't work -- got a parsing error on the digits");
                reformattedTime = userResponse;
            }
        } else if (matcherHours.find()) {
            // todo: refactor this tree to be more readable and efficient
            log.info("Okay, no delimiter, just a couple of digits hence assuming hours");
            List<String> split = Lists.newArrayList(Splitter.on(CharMatcher.anyOf("[ap ]")).omitEmptyStrings().split(trimmedResponse));
            try {
                int hours = Integer.parseInt(split.get(0)) + (pmStringEntered ? 12 : 0);
                reformattedTime = timeJoiner.join(new String[]{String.format("%02d", hours), "00"});
                log.info("Finished up with hours and we have: " + reformattedTime);
            } catch (NumberFormatException e) {
                reformattedTime = userResponse;
            }
        } else {
            log.info("Giving up right at the start, defaulting back to userResponse");
            // give up and hope the natural language parser figures it out
            return trimmedResponse;
        }

        // do this more verbosely first, until we have the logs working
        // return (neededOutput.matcher(reformattedTime).matches()) ? reformattedTime : userResponse;

        if (neededOutput.matcher(reformattedTime).matches()) {
            log.info("It worked! Returning reformatted time ..." + reformattedTime);
            return reformattedTime;
        } else {
            log.info("Got all the way to the end and, nope");
            return trimmedResponse;
        }

    }

    private static final String possibleDateDelims = "[- /.]";
    // todo: make this a bit better in terms of requiring matches
    private static final Pattern dateVariationWithYear = Pattern.compile("\\d{1,2}[- /.]\\d{1,2}[- /.]\\d{4}$");
    private static final Pattern getDateVariationWithOutYear = Pattern.compile("\\d{1,2}[- /.]\\d{1,2}");
    private static final Pattern neededDateOutput = Pattern.compile("^(0?[1-9]|[12][0-9]|3[01])-(0?[1-9]|1[012])-^(19|20)\\d\\d$");
    private static final Joiner dateJoiner = Joiner.on("-").skipNulls();

    public static String reformatDateInput(String userResponse) {
        // todo: as above, make this process properly ... for now, shortcut is to use natural language parser
        String trimmedResponse = userResponse.trim().toLowerCase();
        log.info("This is the response we have ... ");
        final Matcher yearMatcher = dateVariationWithYear.matcher(trimmedResponse);
        final Matcher noYearMatcher = getDateVariationWithOutYear.matcher(trimmedResponse);
        String formattedResponse;
        if (yearMatcher.find()) {
            String dateOnly = yearMatcher.group(0);
            log.info("Looks like we have a valid date string ... " + dateOnly);
            List<String> dividedUp = Lists.newArrayList(Splitter.on(CharMatcher.anyOf(possibleDateDelims)).omitEmptyStrings().split(dateOnly));
            log.info("And now it's sliced apart into this list ... " + dividedUp.toString());
            formattedResponse = dateJoiner.join(new String[]{   String.format("%02d", Integer.parseInt(dividedUp.get(0))),
                                                                String.format("%02d", Integer.parseInt(dividedUp.get(1))),
                                                                dividedUp.get(2)});
        } else if (noYearMatcher.find()) {
            String dateOnly = noYearMatcher.group(0);
            log.info("We have a valid dd-MM string, it looks like ... " + dateOnly);
            List<String> dividedUp = Lists.newArrayList(Splitter.on(CharMatcher.anyOf(possibleDateDelims)).omitEmptyStrings().split(dateOnly));
            log.info("Now it's sliced apart into this list ..." + dividedUp.toString());
            // todo: make the year switch over to next year if date/month is in advance of today
            formattedResponse = dateJoiner.join(new String[]{   String.format("%02d", Integer.parseInt(dividedUp.get(0))),
                                                                String.format("%02d", Integer.parseInt(dividedUp.get(1))),
                                                                Year.now().toString()});
        } else {
            formattedResponse = parseDateTime(userResponse).format(preferredDateFormat);
        }

        return formattedResponse;
    }

    /*
    Quick useful methods for adding hours and minutes to dates and timestamps
     */

    public static Timestamp addHoursFromNow(int numberOfHours) {
        return Timestamp.valueOf(LocalDateTime.now().plusHours(numberOfHours));
    }

    public static Date addHoursToDate(Date date, int numberOfHours) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR, numberOfHours);
        return calendar.getTime();
    }

    public static Date addMinutesToDate(Date date, int numberOfMinutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, numberOfMinutes);
        return calendar.getTime();
    }

    public static Date roundMinutesUp(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, 1);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date roundMinutesDown(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date addMinutesAndTrimSeconds(Date date, int numberOfMinutes) {
        return roundMinutesDown(addMinutesToDate(date, numberOfMinutes));
    }

    public static Date addMinutesAndRoundUp(Date date, int numberOfMinutes) {
        return roundMinutesUp(addMinutesToDate(date, numberOfMinutes));
    }

    public static Date roundHourUp(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR, 1);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date roundHourDown(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND,0);
        return calendar.getTime();
    }

    /*
    Methods to alter dates without altering times, necessarily
     */

    public static Timestamp changeTimestampDates(Timestamp timestamp, String revisedDateString) {
        LocalDate revisedDate;
        try {
            revisedDate = LocalDate.parse(revisedDateString, preferredDateFormat);
        } catch (DateTimeParseException e) {
            revisedDate = LocalDate.from(parseDateTime(revisedDateString));
        }
        return(Timestamp.valueOf(
                changeDatesWithoutChangingTime(timestamp.toLocalDateTime(), revisedDate.atStartOfDay())));
    }

    public static LocalDateTime changeDatesWithoutChangingTime(LocalDateTime origDateTime, LocalDateTime revisedDateTime) {
        return changeDatesWithoutChangingTime(origDateTime, revisedDateTime.getYear(),
                                             revisedDateTime.getMonthValue(), revisedDateTime.getDayOfMonth());
    }

    public static LocalDateTime changeDatesWithoutChangingTime(LocalDateTime origDateTime, int year, int month, int dayOfMonth) {
        return origDateTime.withYear(year).withMonth(month).withDayOfMonth(dayOfMonth);
    }

    public static Timestamp changeTimestampTimes(Timestamp timestamp, String revisedTimeString) {
        LocalTime revisedTime;
        try {
            revisedTime = LocalTime.parse(revisedTimeString, preferredTimeFormat);
        } catch (DateTimeParseException e) {
            revisedTime = LocalTime.from(parseDateTime(revisedTimeString));
        }
        return Timestamp.valueOf(changeTimesWithoutChangingDates(timestamp.toLocalDateTime(),
                                                                 revisedTime.getHour(), revisedTime.getMinute()));
    }

    public static LocalDateTime changeTimesWithoutChangingDates(LocalDateTime origDateTime, int hour, int minute) {
        return origDateTime.withHour(hour).withMinute(minute);
    }

    /*
    Simple method to turn strings from HTML date-time-picker into a Timestamp, if not done within Thymeleaf
     */
    public static Date processDateString(String dateString, SimpleDateFormat sdf) {
        try { return (dateString == null || dateString.trim().equals("")) ?
                null : sdf.parse(dateString); }
        catch (Exception e) { return null; }
    }

    public static Date processDateString(String dateString) {
        return processDateString(dateString, new SimpleDateFormat("dd/MM/yyyy HH:mm a"));
    }

    public static int numberOfMinutesForDays(int days) {
        return 60*24*days;
    }

}
