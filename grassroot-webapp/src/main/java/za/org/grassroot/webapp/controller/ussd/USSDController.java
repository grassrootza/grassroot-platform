package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDMenuUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.util.*;

/**
 * Created by luke on 2015/08/14.
 * todo: Expand -- a lot -- the various methods needed to handle phone number inputs
 */
public class USSDController {

    private Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    UserManagementService userManager;

    @Autowired
    GroupManagementService groupManager;

    @Autowired
    EventManagementService eventManager;

    @Autowired
    @Qualifier("messageSource")
    MessageSource messageSource;

    /*
    Utility classes that pull together some often used methods
     */
    @Autowired
    protected USSDGroupUtil ussdGroupUtil;

    /**
     * SECTION: Constants used throughout the code
     */

    // Constants used in URL mapping and message handling
    protected static final String homePath = USSDUrlUtil.homePath;

    protected static final String
            meetingMenus = "mtg/",
            userMenus = "user/",
            groupMenus = "group/",
            voteMenus = "vote/",
            logMenus = "log/",
            U404= "error";

    // referencing these from the Util class so can be common across tests etc, but stating here so not cumbersome in sub-classes
    protected static final String
            phoneNumber = USSDUrlUtil.phoneNumber,
            userInputParam = USSDUrlUtil.userInputParam,
            groupIdParam = USSDUrlUtil.groupIdParam,
            eventIdParam = USSDUrlUtil.eventIdParam,
            previousMenu = USSDUrlUtil.previousMenu,
            yesOrNoParam = USSDUrlUtil.yesOrNoParam,
            interruptedFlag = USSDUrlUtil.interruptedFlag,
            interruptedInput = USSDUrlUtil.interruptedInput;

    protected static final String
            startMenu = "start",
            groupIdUrlSuffix = USSDUrlUtil.groupIdUrlSuffix,
            eventIdUrlSuffix = USSDUrlUtil.eventIdUrlSuffix,
            doSuffix = "-do";

    // Constants used in i18n and message handling
    protected static final String
            homeKey = USSDSection.HOME.toString(),
            mtgKey = USSDSection.MEETINGS.toString(),
            userKey = USSDSection.USER_PROFILE.toString(),
            groupKey = USSDSection.GROUP_MANAGER.toString(),
            voteKey = USSDSection.VOTES.toString(),
            logKey = USSDSection.LOGBOOK.toString();

    protected static final String
            promptKey = "prompt",
            errorPromptKey = "prompt.error",
            optionsKey = "options.";

    /*
    Stubs to the utility methods, for readability in the sub-classes
     */

    protected Request menuBuilder(USSDMenu ussdMenu) throws URISyntaxException { return USSDMenuUtil.menuBuilder(ussdMenu); }

    /*
    Simple helper method for mocking and unit test
     */

    public void setMessageSource(MessageSource messageSource) { this.messageSource = messageSource; }
    public void setUssdGroupUtil(USSDGroupUtil ussdGroupUtil) { this.ussdGroupUtil = ussdGroupUtil; }

    /**
     * Some default menu returns and some frequently used sets of menu options
     */

    Request tooLongError = new Request("Error! Menu is too long.", new ArrayList<>());
    Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<>());
    Request noGroupError = new Request("Sorry! Something went wrong finding the group.", new ArrayList<>());

    protected Map<String, String> optionsHomeExit(User sessionUser) {
        return ImmutableMap.<String, String>builder().
                put("start", getMessage(startMenu, sessionUser)).
                put("exit", getMessage("exit.option", sessionUser)).build();
    }

    protected Map<String, String> optionsYesNo(User sessionUser, String yesUri, String noUri) {
        return ImmutableMap.<String, String>builder().
                put(yesUri + "&" + yesOrNoParam + "=yes", getMessage(optionsKey + "yes", sessionUser)).
                put(noUri + "&" + yesOrNoParam + "=no", getMessage(optionsKey + "no", sessionUser)).build();
    }

    protected Map<String, String> optionsYesNo(User sesionUser, String nextUri) {
        return optionsYesNo(sesionUser, nextUri, nextUri);
    }

    /*
    i18n helper methods
     */

    protected String getMessage(String section, String menuKey, String messageLocation, User sessionUser) {
        final String messageKey = "ussd." + section + "." + menuKey + "." + messageLocation;
        return messageSource.getMessage(messageKey, null, new Locale(getLanguage(sessionUser)));
    }

    protected String getMessage(USSDSection section, String menu, String messageType, User user) {
        final String messageKey = "ussd." + section.toKey() + menu + "." + messageType;
        return messageSource.getMessage(messageKey, null, new Locale(getLanguage(user)));
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
