package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.enums.USSDSection;

import java.util.Locale;

@Component
public class USSDMessageAssembler {

    private MessageSource messageSource;

    @Autowired
    public USSDMessageAssembler(@Qualifier("messageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // for tests, where we have to hand-wire
    protected void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    protected String getMessage(String key, Object[] params, User user) {
        return messageSource.getMessage("ussd." + key, params, new Locale(getLanguage(user)));
    }

    protected String getMessage(USSDSection section, String menu, String messageType, User user) {
        final String messageKey = "ussd." + section.toKey() + menu + "." + messageType;
        try {
            return messageSource.getMessage(messageKey, null, new Locale(getLanguage(user)));
        } catch (NoSuchMessageException e) {
            return messageSource.getMessage(messageKey, null, new Locale("EN"));
        }
    }

    // convenience function for when passing just a name (of user or group, for example)
    protected String getMessage(USSDSection section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        final String messageKey = "ussd." + section.toKey() + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, new String[]{ parameter }, new Locale(getLanguage(sessionUser)));
    }

    protected String getMessage(USSDSection section, String menu, String messageType, String[] parameters, User user) {
        final String messageKey = "ussd." + section.toKey() + menu + "." + messageType;
        return messageSource.getMessage(messageKey, parameters, new Locale(getLanguage(user)));
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
    protected String getMessage(String messageKey, String language) {
        return messageSource.getMessage("ussd." + messageKey, null, new Locale(language));
    }

    // provides a null safe helper method to get language code from user
    private String getLanguage(User user) {
        return (user.getLanguageCode() == null) ? Locale.US.getLanguage(): user.getLanguageCode();
    }


}
