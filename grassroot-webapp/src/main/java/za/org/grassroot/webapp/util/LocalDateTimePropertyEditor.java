package za.org.grassroot.webapp.util;

import java.beans.PropertyEditorSupport;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * @author Luke Jordan
 */
public class LocalDateTimePropertyEditor extends PropertyEditorSupport{

    public static final String DEFAULT_DATE_PATTERN = "dd/MM/yyyy h:mm a";

    private final DateTimeFormatter simpleDateFormat;


    public LocalDateTimePropertyEditor() {
        this.simpleDateFormat = DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN);
    }

    public LocalDateTimePropertyEditor(DateTimeFormatter dtf) {
        this.simpleDateFormat = dtf;
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        try {
            setValue(this.simpleDateFormat.parse(text, LocalDateTime::from));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Could not parse date: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getAsText() {
        LocalDateTime value = (LocalDateTime) getValue();
        return (value != null) ? this.simpleDateFormat.format(value) : "";
    }
}
