package za.org.grassroot.webapp.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.enums.USSDSection;

import java.util.Locale;

/**
 * Created by luke on 2015/12/04.
 */
abstract class USSDUtil {

    // we use this for truncating lists of votes, meetings, etc -- roughly 50 chars for prompt, 5 chars for ellipsis & enum
    private static final int maxSafeListOptionLength = ((160 - 50) / 3) - 5;

    protected final MessageSource messageSource;

    protected static final Integer PAGE_LENGTH = 3;
    protected static final String
            promptKey = "prompt",
            optionsKey = "options.";

    protected USSDUtil(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    protected String checkAndTruncateMenuOption(String option) {
        if (option.length() <= maxSafeListOptionLength) return option;
        else return option.substring(0, maxSafeListOptionLength) + "...";
    }

    protected String getMessage(String section, String menuKey, String messageLocation, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    protected String getMessage(USSDSection section, String menu, String messageType, User user) {
        final String messageKey = "ussd." + section.toKey() + menu + "." + messageType;
        try {
            return messageSource.getMessage(messageKey, null, new Locale(getLanguage(user)));
        } catch (NoSuchMessageException e) {
            return messageSource.getMessage(messageKey, null, new Locale("en"));
        }
    }

    // note: this should be deprecated ... should only use method that passes Section -- leaving for moment though
    protected String getMessage(String section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        return getMessage(USSDSection.fromString(section), menuKey, messageLocation, parameter, sessionUser);
    }

    // convenience function for when passing just a name (of user or group, for example)
    protected String getMessage(USSDSection section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        final String messageKey = "ussd." + section.toKey() + menuKey + "." + messageLocation;
        try {
            return messageSource.getMessage(messageKey, new String[]{parameter}, new Locale(getLanguage(sessionUser)));
        } catch(NoSuchMessageException e) {
            return messageSource.getMessage(messageKey, new String[]{parameter}, new Locale("en"));
        }
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
