package za.org.grassroot.webapp.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import za.org.grassroot.core.domain.User;

import java.util.Locale;

/**
 * Created by luke on 2015/12/04.
 */
public class USSDUtil {

    @Autowired
    MessageSource messageSource;

    protected static final Integer PAGE_LENGTH = 3;
    protected static final String
            promptKey = "prompt",
            optionsKey = "options.";

    public void setMessageSource(MessageSource messageSource) { this.messageSource = messageSource; }

    protected String getMessage(String section, String menuKey, String messageLocation, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    // convenience function for when passing just a name (of user or group, for example)
    protected String getMessage(String section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, new String[]{ parameter }, new Locale(getLanguage(sessionUser)));
    }

    protected String getMessage(String section, String menuKey, String messageLocation, String[] parameters, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, parameters, new Locale(getLanguage(sessionUser)));
    }

    // for convenience, sometimes easier to read this way than passing around user instance
    protected String getMessage(String section, String menuKey, String messageLocation, Locale sessionLocale) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, sessionLocale);
    }

    // final convenience version, for the root strings, stripping out "."
    protected String getMessage(String messageKey, User sessionUser) {
        return messageSource.getMessage("ussd." + messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    // todo move this somewhere else, and/or clean up nullability in User class, but if put it there, confuses Hibernate (wants a setter)
    protected String getLanguage(User user) {
        // todo some validation on the locale code, above just checking it's not null
        return (user.getLanguageCode() == null) ? Locale.US.getLanguage(): user.getLanguageCode();
    }
}
