package za.org.grassroot.core.util;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class DateTimeUtil {

    private static final DateTimeFormatter preferredDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter preferredTimeFormat = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter preferredDateTimeFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final DateTimeFormatter preferredRestFormat = DateTimeFormatter.ISO_DATE_TIME;

    private static final LocalDateTime veryLongTimeAway = LocalDateTime.of(2099, 12, 31, 23, 59);
    private static final Instant earliestInstant = LocalDateTime.of(2015, 1, 1, 0 , 0).toInstant(ZoneOffset.UTC);

    private static final ZoneId zoneSAST = ZoneId.of("Africa/Johannesburg");
    private static final ZoneId zoneSystem = ZoneId.systemDefault();

    private static final int latestHourForAutomatedMessages = 20;
    private static final int earliestHourForAutomatedMessage = 8;
    private static final LocalTime latestHour = LocalTime.of(latestHourForAutomatedMessages, 0);
    private static final LocalTime earliestHour = LocalTime.of(earliestHourForAutomatedMessage, 0);

    // we use a lot of these, because they are cheap to cycle through, and Java 8 parsing is
    // highly esoteric in how it actually handles digit length, optional delimiters, etc
    private static DateTimeFormatter buildFormatter(String pattern) {
        return new DateTimeFormatterBuilder().appendPattern(pattern)
                .parseDefaulting(ChronoField.YEAR_OF_ERA, Year.now().getValue()).toFormatter();
    }

    private static final List<DateTimeFormatter> commonDateTimeFormats = Arrays.asList(
            buildFormatter("dd[-][ ]MM H[:][ ]['H']mm"),
            buildFormatter("dd[-][ ]MM[-][ ]yyyy H[:][ ]['H']mm"),
            buildFormatter("dd[-][ ]MM[-][ ]yyyy h[:][ ]['H']mm[ ]a"),
            buildFormatter("dd[-][ ]MM[-][ ]yy H[:][ ]['H']mm"),
            buildFormatter("dd[-][ ]MM[-][ ]yy h[:][ ]['H']mm[ ]a")
    );

    private static final List<DateTimeFormatter> commonDateFormats = Arrays.asList(
            buildFormatter("dd[-][ ][.]MM"),
            buildFormatter("dd[-][ ][.]MM[-][ ][.]yy"),
            buildFormatter("dd[-][ ][.]MM[-][ ][.]yyyy")
    );

    private static final List<DateTimeFormatter> commonTimeFormats = Arrays.asList(
            buildFormatter("H[:][ ]['H']mm"),
            buildFormatter("h[:][ ]['H']mm[ ]a"),
            buildFormatter("Ha")
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
    private static LocalDateTime tryToParse(String input, DateTimeFormatter formatter, boolean dateOnly, boolean timeOnly) {
        try {
            if (timeOnly) {
                LocalTime parsed = LocalTime.parse(input, formatter);
                log.info("Succeeded on time only! Parsed {} to {}", input, parsed);
                return parsed.atDate(LocalDate.now());
            } else if (dateOnly) {
                LocalDate parsed = LocalDate.parse(input, formatter);
                log.info("Succeeded on date only! Parsed {} to {}", input, parsed);
                return parsed.atStartOfDay();
            } else {
                LocalDateTime parsed = LocalDateTime.parse(input, formatter);
                log.info("Succeeded! Parsed {} to {}", input, parsed);
                return parsed;
            }
        } catch (DateTimeParseException e) {
            log.debug("Parse error, formatter: {}, error: {}", formatter, e.getMessage());
            return null;
        }
    }

    public static LocalDateTime tryParseString(String userResponse) {
        final String input = userResponse.trim().toUpperCase();
        log.info("Trying formal parse of {}", input);

        Optional<LocalDateTime> dateTimeAttempt = commonDateTimeFormats.stream()
                .map(format -> tryToParse(input, format, false, false)).filter(Objects::nonNull).findFirst();

        log.info("result of date time attempt: {}", dateTimeAttempt);
        return dateTimeAttempt.orElseGet(()
                -> tryParseDate(input).orElseGet(()
                -> tryParseTime(input).orElse(null)));
    }

    private static Optional<LocalDateTime> tryParseDate(String input) {
        return commonDateFormats.stream().map(formatter -> tryToParse(input, formatter, true, false)).filter(Objects::nonNull).findFirst();
    }

    private static Optional<LocalDateTime> tryParseTime(String input) {
        return commonTimeFormats.stream().map(formatter -> tryToParse(input, formatter, false, true)).filter(Objects::nonNull).findFirst();
    }

    public static String reformatTimeInput(String userResponse) {
        String trimmedResponse = userResponse.trim().toUpperCase(); // since pm expected in upper case
        log.info("Trying to harmonize a time input: {}", trimmedResponse);
        return tryParseTime(trimmedResponse).map(ldt -> ldt.format(preferredTimeFormat)).orElse(trimmedResponse);
    }

    public static String reformatDateInput(String userResponse) {
        String trimmedResponse = userResponse.trim().toLowerCase();
        log.info("Given as input: {}, trimmed to: {}", userResponse, trimmedResponse);
        return tryParseDate(trimmedResponse).map(ldt -> ldt.format(preferredDateFormat)).orElse(trimmedResponse);
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

    public static LocalDate getStartTimeForEntityStats(Integer year, Integer month, Instant entityCreationTime) {
        if (year != null && month != null)
            return LocalDate.of(year, month, 1);
        else if (year != null)
            return LocalDate.of(year, 1, 1);
        else
            return entityCreationTime.atZone(Clock.systemDefaultZone().getZone()).toLocalDate();
    }

    public static LocalDate getEndTime(Integer year, Integer month, LocalDate startTime) {
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
