package za.org.grassroot.core.util;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 9/19/15.
 */
public class DateTimeUtil {

    private static Logger log = Logger.getLogger("DateTimeUtil");


        /*
        Inserting method to parse date time user input and, if it can be parsed, set the timestamp accordingly.
        todo: a lot of error handling and looking through the tree to make sure this is right.
        todo: come up with a more sensible default if the parsing fails, rather than current time
        todo: work on handling methods / customize the util library to handle local languages
        todo: in the next menu, ask if this is right, and, if not, option to come back
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

    public static Date addHoursToDate(Date date, int numberOfHours) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR,numberOfHours);
        return calendar.getTime();
    }

    public static Date addMinutesToDate(Date date, int numberOfMinutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE,numberOfMinutes);
        return calendar.getTime();
    }

    public static Date roundMinutesUp(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE,1);
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
        calendar.add(Calendar.HOUR,1);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    public static Date roundHourDown(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.MINUTE,0);
        calendar.set(Calendar.SECOND,0);
        calendar.set(Calendar.MILLISECOND,0);
        return calendar.getTime();
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

}
