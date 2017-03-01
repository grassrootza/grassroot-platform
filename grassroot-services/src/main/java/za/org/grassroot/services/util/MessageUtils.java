package za.org.grassroot.services.util;

import za.org.grassroot.core.domain.User;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Created by luke on 2017/03/01.
 */
public class MessageUtils {

    public static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

    public static Locale getUserLocale(User user) {
        final String languageCode = user.getLanguageCode();
        if (languageCode == null || languageCode.trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(languageCode);
        }
    }

}
