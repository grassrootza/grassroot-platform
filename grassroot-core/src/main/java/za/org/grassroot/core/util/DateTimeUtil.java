package za.org.grassroot.core.util;

import org.apache.commons.collections.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Created by aakilomar on 9/19/15.
 */
public class DateTimeUtil {

    private static final Logger log = LoggerFactory.getLogger(DateTimeUtil.class);

    /*
    Initial set of variabels is for common date / time manipulation
     */

    private static final DateTimeFormatter preferredDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter preferredTimeFormat = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter preferredDateTimeFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final DateTimeFormatter preferredRestFormat = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter webFormFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm a");

    private static final LocalDateTime veryLongTimeAway = LocalDateTime.of(2099, 12, 31, 23, 59);
    private static final Instant earliestInstant = LocalDateTime.of(2015, 1, 1, 0 , 0).toInstant(ZoneOffset.UTC);

    private static final ZoneId zoneSAST = ZoneId.of("Africa/Johannesburg");
    private static final ZoneId zoneSystem = ZoneId.systemDefault();

    private static final int latestHourForAutomatedMessages = 20;
    private static final int earliestHourForAutomatedMessage = 8;
    private static final LocalTime latestHour = LocalTime.of(latestHourForAutomatedMessages, 0);
    private static final LocalTime earliestHour = LocalTime.of(earliestHourForAutomatedMessage, 0);

    /*
    some delimiters, patterns for regex, etc (may be able to remove, given Selo / SUTime)
     */

    private static final String possibleDateDelimiters = "[- /.]";

    // we use a lot of these, because they are cheap to cycle through, and Java 8 parsing is
    // highly esoteric in how it actually handles digit length, optional delimiters, etc
    private static final List<DateTimeFormatter> commonDateTimeFormats = Arrays.asList(
            DateTimeFormatter.ofPattern("dd-MM HH:mm"),
            DateTimeFormatter.ofPattern("dd MM HHmm"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH[.]mm"),
            DateTimeFormatter.ofPattern("d-M-yy HH[:]mm[a]"),
            DateTimeFormatter.ofPattern("d-M-yy HH['h']mm"),
            DateTimeFormatter.ofPattern("d-M-yyyy HH[:]mm"),
            DateTimeFormatter.ofPattern("d-M-yyyy HH['h']mm"),
            DateTimeFormatter.ofPattern("d-M HH[:]mm"),
            DateTimeFormatter.ofPattern("d-M HH['h']mm")
    );

    private static final List<DateTimeFormatter> commonDateFormats = Arrays.asList(
            DateTimeFormatter.ofPattern("dd-MM"),
            DateTimeFormatter.ofPattern("dd MM"),
            DateTimeFormatter.ofPattern("ddMM"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d M yy"),
            DateTimeFormatter.ofPattern("d-M yy"),
            DateTimeFormatter.ofPattern("d.M.yy"),
            DateTimeFormatter.ofPattern("d M yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("ddMMyyyy")
    );

    private static final List<DateTimeFormatter> commonTimeFormats = Arrays.asList(
            DateTimeFormatter.ofPattern("HH['h']mm"),
            DateTimeFormatter.ofPattern("HH-mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("hh:mm[a]"),
            DateTimeFormatter.ofPattern("hh mm a"),
            DateTimeFormatter.ofPattern("HH mm"),
            DateTimeFormatter.ofPattern("hh mm")
    );

    public static ZoneId getSAST() { return zoneSAST; }

    public static DateTimeFormatter getPreferredDateFormat() { return preferredDateFormat; }
    public static DateTimeFormatter getPreferredTimeFormat() { return preferredTimeFormat; }
    public static DateTimeFormatter getPreferredDateTimeFormat() { return preferredDateTimeFormat; }
    public static DateTimeFormatter getPreferredRestFormat() { return preferredRestFormat; }

    public static Instant getVeryLongAwayInstant() { return veryLongTimeAway.toInstant(ZoneOffset.UTC); }
    public static Instant getEarliestInstant() { return earliestInstant; }

    public static Instant convertToSystemTime(LocalDateTime userInput, ZoneId userZoneId) {
        Objects.requireNonNull(userInput, "Local date time is required");
        Objects.requireNonNull(userZoneId);

        ZonedDateTime userTime = ZonedDateTime.of(userInput, userZoneId);
        ZonedDateTime systemTime = userTime.withZoneSameInstant(zoneSystem);

        return systemTime.toInstant();
    }

    public static ZonedDateTime convertToUserTimeZone(Instant timeInSystem, ZoneId userZoneId) {
        return timeInSystem.atZone(userZoneId);
    }

    public static String formatAtSAST(Instant instant, DateTimeFormatter format) {
        return instant.atZone(zoneSAST).format(format);
    }

    public static Instant restrictToDaytime(Instant instantToRestrict, Instant thresholdTime, ZoneId userZoneId) {
        ZonedDateTime zonedDateTime = instantToRestrict.atZone(userZoneId);
        if (zonedDateTime.getHour() <= earliestHourForAutomatedMessage) {
            zonedDateTime = ZonedDateTime.of(zonedDateTime.toLocalDate(), earliestHour, userZoneId);
            return thresholdTime == null || zonedDateTime.toInstant().isBefore(thresholdTime) ? zonedDateTime.toInstant() : thresholdTime;
        } else if (zonedDateTime.getHour() >= latestHourForAutomatedMessages) {
            zonedDateTime = ZonedDateTime.of(zonedDateTime.toLocalDate(), latestHour, userZoneId);
            return zonedDateTime.toInstant();
        } else {
            return instantToRestrict;
        }
    }

    /*
    SECTION : regex for handling preformatted date time (may be able to remove given introduction of Selo & SUTime)
     */
    public static LocalDateTime tryParseString(String userResponse) {
        log.info("Trying formal parse of {}", userResponse);
        final List<DateTimeFormatter> allCommonFormats = new ArrayList<>(commonDateTimeFormats);
        allCommonFormats.addAll(commonDateFormats);
        allCommonFormats.addAll(commonTimeFormats);
        for (DateTimeFormatter formatter : allCommonFormats) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(userResponse, formatter);
                log.info("Succeeded! Parsed to {}", parsed);
                return parsed;
            } catch (DateTimeParseException e) {
                log.debug("That one didn't work, formatter: {}, user input: {}", formatter, userResponse);
            }
        }
        log.info("No formatter caught that, returning null");
        return null;
    }

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
        String trimmedResponse = userResponse.trim().toUpperCase(); // since pm expected in upper case
        log.info("Trying to harmonize a time input: {}", trimmedResponse);

        LocalTime localTime = null;
        List<DateTimeFormatter> formatters = ListUtils.union(commonTimeFormats, commonDateTimeFormats);
        for (DateTimeFormatter formatter : formatters) {
            try {
                localTime = LocalTime.parse(trimmedResponse, formatter);
                log.info("Succeeded, local time parsed as : {}", localTime);
            } catch (DateTimeParseException e) {
                log.debug("Failed using formatter {} for {}", formatter, trimmedResponse);
            }
        }

        return localTime == null ? trimmedResponse : localTime.format(preferredTimeFormat);
    }

    /**
     * Helper method, similar to reformatTimeInput, that checks user input against common/suggested date formats, and
     * returns formatted as dd-mm-yyyy, or else just returns the string if no pattern matches
     * @param userResponse
     * @return String reformatted, if understood, else the paramater as-is
     */
    public static String reformatDateInput(String userResponse) {
        String trimmedResponse = userResponse.trim().toLowerCase();
        log.info("Given as input: {}, trimmed to: {}", userResponse, trimmedResponse);
        LocalDate localDate = null;
        List<DateTimeFormatter> formatters = new ArrayList<>(commonDateFormats);
        for (DateTimeFormatter formatter : formatters) {
            try {
                localDate = LocalDate.parse(trimmedResponse, formatter);
                log.info("Succeeded, local date parsed as : {}", localDate);
            } catch (DateTimeParseException e) {
                log.info("Failed using formatter {} for {}", formatter, trimmedResponse);
            }
        }
        log.info("reformDateInput .... at conclusion, trying to return: {}", localDate);
        return localDate == null ? trimmedResponse : localDate.format(preferredDateFormat);
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

    public static LocalDate getStartTimeForEntityStats(@Nullable Integer year, @Nullable Integer month, @Nonnull Instant entityCreationTime) {
        if (year != null && month != null)
            return LocalDate.of(year, month, 1);
        else if (year != null)
            return LocalDate.of(year, 1, 1);
        else
            return entityCreationTime.atZone(Clock.systemDefaultZone().getZone()).toLocalDate();
    }

    public static LocalDate getEndTime(@Nullable Integer year, @Nullable Integer month, @Nonnull  LocalDate startTime) {
        if (year != null && month != null) {
            LocalDate endDay = startTime.plus(1, ChronoUnit.MONTHS);
            LocalDate thisDay = LocalDate.now();
            if (thisDay.isBefore(endDay)) {
                endDay = LocalDate.of(thisDay.getYear(), thisDay.getMonthValue(), thisDay.getDayOfMonth()).plusDays(1);
            }
            return endDay;
        } else if (year != null) {
            LocalDate endMonth = startTime.plus(1, ChronoUnit.YEARS);
            LocalDate thisMOnth = LocalDate.now();
            thisMOnth = LocalDate.of(thisMOnth.getYear(), thisMOnth.getMonthValue(), 1);
            if (thisMOnth.isBefore(endMonth))
                endMonth = LocalDate.of(thisMOnth.getYear(), thisMOnth.getMonthValue(), 1).plusMonths(1);
            return endMonth;
        } else {
            LocalDate today = LocalDate.now();
            return LocalDate.of(today.getYear(), today.getMonth(), 1).plusMonths(1);
        }
    }

}
