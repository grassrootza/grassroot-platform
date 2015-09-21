package za.org.grassroot.webapp.util;

import java.beans.PropertyEditorSupport;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * @author Lesetse Kimwaga
 */
public class SqlTimestampPropertyEditor extends PropertyEditorSupport{

    public static final String DEFAULT_DATE_PATTERN = "dd/MM/yyyy h:mm a";

    private final SimpleDateFormat simpleDateFormat;


    public SqlTimestampPropertyEditor() {
        this.simpleDateFormat = new SimpleDateFormat(DEFAULT_DATE_PATTERN);
    }

    public SqlTimestampPropertyEditor(String pattern) {
        this.simpleDateFormat = new SimpleDateFormat(pattern);
    }


    @Override
    public void setAsText(String text) throws IllegalArgumentException {

        try {
            setValue(new Timestamp(this.simpleDateFormat.parse(text).getTime()));
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Could not parse date: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String getAsText() {
        Timestamp value = (Timestamp) getValue();
        return (value != null ? this.simpleDateFormat.format(value) : "");
    }
}
