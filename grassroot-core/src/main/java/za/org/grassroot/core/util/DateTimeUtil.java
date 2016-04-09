package za.org.grassroot.core.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import za.org.grassroot.core.util.natty.src.main.java.com.joestelmach.natty.DateGroup;
import za.org.grassroot.core.util.natty.src.main.java.com.joestelmach.natty.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by aakilomar on 9/19/15.
 */
public class DateTimeUtil {

    private static final Logger log = LoggerFactory.getLogger(DateTimeUtil.class);

    private static final DateTimeFormatter preferredDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter preferredTimeFormat = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter preferredDateTimeFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final DateTimeFormatter preferredRestFormat = DateTimeFormatter.ISO_DATE_TIME;

    private static final LocalDateTime veryLongTimeAway = LocalDateTime.of(2099, 12, 31, 23, 59);

    public static DateTimeFormatter getPreferredDateFormat() { return preferredDateFormat; }
    public static DateTimeFormatter getPreferredTimeFormat() { return preferredTimeFormat; }
    public static DateTimeFormatter getPreferredDateTimeFormat() { return preferredDateTimeFormat; }
    public static DateTimeFormatter getPreferredRestFormat() { return preferredRestFormat; }

    public static LocalDateTime getVeryLongTimeAway() {
        return veryLongTimeAway;
    }
    public static Timestamp getVeryLongTimestamp() {
        return Timestamp.valueOf(veryLongTimeAway);
    }

    private static final ZoneId zoneSAST = ZoneId.of("Africa/Johannesburg");
    private static final ZoneId zoneSystem = ZoneId.systemDefault();

    /**
     * Method that invokes the date time parser directly, with no attempt to map to a predefined format or regex
     * @param passedValue
     * @return LocalDateTime of most likely match; if no match, returns the current date time rounded up to next hour
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
            parsedDateTime = LocalDateTime.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        }

        return parsedDateTime;
    }

    public static Instant convertToSystemTime(LocalDateTime userInput, ZoneId userZoneId) {
        ZonedDateTime userTime = ZonedDateTime.of(userInput, userZoneId);
        ZonedDateTime systemTime = userTime.withZoneSameInstant(zoneSystem);

        log.info("Time at user: {}, time at system: {}", userTime.format(DateTimeFormatter.ISO_DATE_TIME),
                 systemTime.format(DateTimeFormatter.ISO_DATE_TIME));

        return systemTime.toInstant();
    }

    public static ZonedDateTime convertToUserTimeZone(Instant timeInSystem, ZoneId userZoneId) {
        ZonedDateTime zonedDateTime = timeInSystem.atZone(userZoneId);
        log.info("Time in system: {}, converted to zone: {}", timeInSystem.toString(), zonedDateTime.format(DateTimeFormatter.ISO_DATE_TIME));
        return zonedDateTime;
    }

    public static ZoneId getSAST() { return zoneSAST; }

    private static final String possibleTimeDelimiters = "[-,:hH]+";
    private static final String meridian = ".*pm?";
    private static final Joiner timeJoiner = Joiner.on(":").skipNulls();
    private static final Pattern timePatternWithDelimiters = Pattern.compile("\\d{1,2}" + possibleTimeDelimiters + "\\d\\d");
    private static final Pattern timePatternWithoutDelimiters = Pattern.compile("\\d{3,4}");
    private static final Pattern timeWithHourOnly = Pattern.compile("\\d{1,2}[am|pm]?");
    private static final Pattern neededTimePattern = Pattern.compile("\\d{2}:\\d{2}");

    private static final String possibleDateDelimiters = "[- /.]";
    private static final Joiner dateJoiner = Joiner.on("-").skipNulls();
    private static final Pattern datePatternWithYear = Pattern.compile("\\d{1,2}[- /.]\\d{1,2}[- /.]\\d{4}$");
    private static final Pattern datePatternWithoutYear = Pattern.compile("\\d{1,2}[- /.]\\d{1,2}");
    private static final Pattern datePatternWithoutDelimiters = Pattern.compile("\\d{3,4}");
    private static final Pattern neededDatePattern = Pattern.compile("\\d{2}-\\d{2}-\\d{4}");

    /**
     * Helper method to deal with messy user input of a time string, more strict but also more likely to be accurate than
     * free form parsing above. The menu prompt does give the preferred format of HH:mm, but never know, so check for a
     * range of possible delimiters, and three basic patterns -- 15:30, 3:30 pm, 1530. Since there may be stuff around the
     * outside, all we care about is finding a match, not the whole input matching, and then whether we have to add 12 to
     * hour if 'pm' has been entered. We check for those, then give up if none work
     * @param userResponse The string that the user has entered
     * @return The string reformated as mm:HH, if it matched any of the known patterns, else the string as-is
     */

    public static String reformatTimeInput(String userResponse) {

        String reformattedTime;

        final String trimmedResponse = userResponse.trim().toLowerCase().replace(possibleTimeDelimiters,":");
        boolean pmEntered = Pattern.matches(meridian, trimmedResponse);

        log.info("reformTimeInput... given this as input:{}, trimmed to this: {}, and found pm: {}", userResponse, trimmedResponse, pmEntered);

        final Matcher tryMatchWithDelimiters = timePatternWithDelimiters.matcher(trimmedResponse);
        final Matcher tryMatchWithoutDelimiters = timePatternWithoutDelimiters.matcher(trimmedResponse);
        final Matcher tryMatchOnlyHavingHours = timeWithHourOnly.matcher(trimmedResponse);

        if (tryMatchWithDelimiters.find()) {

            String timeWithoutDelims = tryMatchWithDelimiters.group(0);
            log.info("reformatTimeInput ... matched XX:YY, or variant, extracted as: {}", timeWithoutDelims);
            List<String> splitDigits = Lists.newArrayList(
                    Splitter.on(CharMatcher.anyOf(possibleTimeDelimiters)).omitEmptyStrings().split(timeWithoutDelims));
            int hours = Integer.parseInt(splitDigits.get(0)) + (pmEntered ? 12 : 0);
            reformattedTime = timeJoiner.join(new String[]{String.format("%02d", hours), splitDigits.get(1)});

        } else if (tryMatchWithoutDelimiters.find()) {

            log.info("reformatTimeInput ... no delimiter, 3-4 digits in a row, assuming those are it ...");
            String digitString = tryMatchWithoutDelimiters.group(0);
            int digitsForHours = (Integer.parseInt(digitString.substring(0, 2)) < 13) ? 2 : 1;
            reformattedTime = timeJoiner.join(new String[]{
                    String.format("%02d", Integer.parseInt(digitString.substring(0, digitsForHours))),
                    String.format("%02d", Integer.parseInt(digitString.substring(digitString.length() - digitsForHours))) });

        } else if (tryMatchOnlyHavingHours.find()) {

            log.info("reformatTimeInput ... no delimiter, only a couple digits, assuming hours ...");
            List<String> split = Lists.newArrayList(
                    Splitter.on(CharMatcher.anyOf("[ap ]")).omitEmptyStrings().split(trimmedResponse));
            try {
                int hours = Integer.parseInt(split.get(0)) + (pmEntered ? 12 : 0);
                reformattedTime = timeJoiner.join(new String[]{String.format("%02d", hours), "00"});
            } catch (NumberFormatException e) {
                log.info("reformatTimeInput error when trying to parse hours, return string without white space");
                return trimmedResponse;
            }

        } else {
            log.info("reformatTimeInput ... no patterns matched, return the string just without white space");
            return trimmedResponse;
        }

        log.info("reformatTimeInput .... at conclusion, trying to return: {}" + reformattedTime);
        return (neededTimePattern.matcher(reformattedTime).matches()) ? reformattedTime : trimmedResponse;

    }

    /**
     * Helper method, similar to reformatTimeInput, that checks user input against common/suggested date formats, and
     * returns formatted as dd-mm-yyyy, or else just returns the string if no pattern matches
     * @param userResponse
     * @return String reformatted, if understood, else the paramater as-is
     */
    public static String reformatDateInput(String userResponse) {

        String trimmedResponse = userResponse.trim().toLowerCase().replace(possibleDateDelimiters,"-");

        log.info("reformDateInput... given this as input:{}, trimmed to this: {}", userResponse, trimmedResponse);

        final Matcher yearMatcher = datePatternWithYear.matcher(trimmedResponse);
        final Matcher noYearMatcher = datePatternWithoutYear.matcher(trimmedResponse);
        final Matcher noDelimMatcher = datePatternWithoutDelimiters.matcher(trimmedResponse);

        String reformattedDate;

        if (yearMatcher.find()) {

            String dateOnly = yearMatcher.group(0);
            log.info("reformDateInput ... valid date string with years: {}", dateOnly);
            List<String> dividedUp = Lists.newArrayList(
                    Splitter.on(CharMatcher.anyOf(possibleDateDelimiters)).omitEmptyStrings().split(dateOnly));
            reformattedDate = dateJoiner.join(new String[]{
                    String.format("%02d", Integer.parseInt(dividedUp.get(0))), // day
                    String.format("%02d", Integer.parseInt(dividedUp.get(1))), // month
                    dividedUp.get(2)}); // year

        } else if (noYearMatcher.find()) {

            String dateOnly = noYearMatcher.group(0);
            log.info("reformDateInput .... valid dd-MM string found, extracted as: {}", dateOnly);
            List<String> dividedUp = Lists.newArrayList(
                    Splitter.on(CharMatcher.anyOf(possibleDateDelimiters)).omitEmptyStrings().split(dateOnly));
            String year = (Integer.parseInt(dividedUp.get(1)) >= LocalDateTime.now().getMonthValue()) ?
                    Year.now().toString() : Year.now().plusYears(1).toString();
            reformattedDate = dateJoiner.join(new String[]{
                    String.format("%02d", Integer.parseInt(dividedUp.get(0))),
                    String.format("%02d", Integer.parseInt(dividedUp.get(1))), year});

        } else if (noDelimMatcher.find()) {

            try {
                log.info("reformDateInput ... no delimiter, found 3-4 digits in a row, assuming those are it ..");
                String digitString = noDelimMatcher.group(0);
                // not failsafe, but as good as we can make it (will interpret 104 as 1 april, most of rest will get)
                int digitsForMonths = (Integer.parseInt(digitString.substring(digitString.length() - 2)) < 13) ? 2 : 1;
                String month = digitString.substring(digitString.length() - digitsForMonths);
                String days = digitString.substring(0, digitString.length() - digitsForMonths);
                String year = (Integer.parseInt(month) >= LocalDateTime.now().getMonthValue()) ?
                        Year.now().toString() : Year.now().plusYears(1).toString();
                reformattedDate = dateJoiner.join(new String[]{
                        String.format("%02d", Integer.parseInt(days)),
                        String.format("%02d", Integer.parseInt(month)), year});
            } catch (Exception e) { // lots could go wrong
                log.info("reformDateInput ... something went wrong: {}", e.toString());
                return trimmedResponse;
            }

        } else {
            return trimmedResponse;
        }

        log.info("reformDateInput .... at conclusion, trying to return: {}" + reformattedDate);
        return (neededDatePattern.matcher(reformattedDate).matches()) ? reformattedDate : trimmedResponse;
    }

    /**
     * Method that will take a string representing a date and update a timestamp, leaving the time unchanged. It will
     * first try to process the string in the preferred format; if it fails, it invokes the language processor
     * @param originalDateTime The original date and time
     * @param revisedDateString The string representing the new date
     * @return The revised timestamp, with the new date, but original time
     */
    public static LocalDateTime changeTimestampDates(LocalDateTime originalDateTime, String revisedDateString) {
        Objects.requireNonNull(originalDateTime);
        Objects.requireNonNull(revisedDateString);

        LocalDate revisedDate;
        try {
            revisedDate = LocalDate.parse(revisedDateString, preferredDateFormat);
        } catch (DateTimeParseException e) {
            revisedDate = LocalDate.from(parseDateTime(revisedDateString));
        }
        LocalDateTime newDateTime = LocalDateTime.of(revisedDate, originalDateTime.toLocalTime());
        return newDateTime;
    }

    /**
     * Method that will take a string representing a time and update an instant, leaving the date unchanged. It will
     * first try to process the string in the preferred time format ("HH:mm"); if it fails, it invokes the language processor
     * @param originalDateTime The original local date time
     * @param revisedTimeString The string representing the new time
     * @return The revised timestamp, with the new date, but original time
     */
    public static LocalDateTime changeTimestampTimes(LocalDateTime originalDateTime, String revisedTimeString) {
        Objects.requireNonNull(originalDateTime);
        Objects.requireNonNull(revisedTimeString);

        LocalTime revisedTime;
        try {
            revisedTime = LocalTime.parse(revisedTimeString, preferredTimeFormat);
        } catch (DateTimeParseException e) {
            revisedTime = LocalTime.from(parseDateTime(revisedTimeString));
        }
        LocalDateTime newDateTime = LocalDateTime.of(originalDateTime.toLocalDate(), revisedTime);
        return newDateTime;
    }

    /**
     * Method that takes a string already preformatted to conform to preferred date format, and returns a LocalDateTime
     * that corresponds to that date, at a specific hour and minute
     * @param formattedValue The date string, pre-formatted to the pattern "dd-MM-yyyy"
     * @param hour The hour at which to set the local date time
     * @param minute The minute at which to set the local date time
     * @return A local date time object representing the provided string at the specified hour and minute
     */
    public static LocalDateTime convertDateStringToLocalDateTime(String formattedValue, int hour, int minute) {
        Objects.requireNonNull(formattedValue);
        if ((hour < 0 || hour > 24) || (minute < 0 || minute > 60)) {
            throw new IllegalArgumentException("Illegal argument! Hours and minutes must be in range 0-24, 0-60");
        }
        try {
            final LocalDate parsedDate = LocalDate.parse(formattedValue, preferredDateFormat);
            return LocalDateTime.of(parsedDate, LocalTime.of(hour, minute));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Illegal argument! Date string not formatted as dd-MM-yyyy");
        }
    }

    public static int numberOfMinutesForDays(int days) {
        return 60 * 24 * days;
    }

}
