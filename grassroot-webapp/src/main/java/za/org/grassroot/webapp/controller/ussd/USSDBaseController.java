package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.ImmutableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.experiments.ExperimentBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDMenuUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static za.org.grassroot.webapp.enums.USSDSection.*;

/**
 * Created by luke on 2015/08/14.
 *
 */
@Controller
public class USSDBaseController {

    @Autowired
    private ExperimentBroker experimentBroker;

    @Autowired
    protected UserManagementService userManager;

    @Autowired
    protected AsyncUserLogger userLogger;

    @Autowired
    protected CacheUtilService cacheManager;

    protected USSDMessageAssembler messageAssembler;
    protected USSDMenuUtil ussdMenuUtil;

    @Autowired
    protected void setUssdMenuUtil(USSDMenuUtil ussdMenuUtil) {
        this.ussdMenuUtil = ussdMenuUtil;
    }

    @Autowired
    protected void setMessageAssembler(USSDMessageAssembler messageAssembler) {
        this.messageAssembler = messageAssembler;
    }

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
            todoMenus = "todo/",
            safetyMenus = "safety/",
            moreMenus = "more/",
            U404= "error",
            homeMore = "/more/";
    // referencing these from the Util class so can be common across tests etc, but stating here so not cumbersome in sub-classes
    protected static final String
            phoneNumber = USSDUrlUtil.phoneNumber,
            userInputParam = USSDUrlUtil.userInputParam,
            groupUidParam = USSDUrlUtil.groupUidParam,
            entityUidParam = USSDUrlUtil.entityUidParam,
            previousMenu = USSDUrlUtil.previousMenu,
            yesOrNoParam = USSDUrlUtil.yesOrNoParam,
            interruptedFlag = USSDUrlUtil.interruptedFlag,
            interruptedInput = USSDUrlUtil.interruptedInput,
            revisingFlag = USSDUrlUtil.revisingFlag;
    protected static final String
            startMenu = "start",
            groupUidUrlSuffix = USSDUrlUtil.groupUidUrlSuffix,
            entityUidUrlSuffix = USSDUrlUtil.entityUidUrlSuffix,
            doSuffix = "-do";
    // Constants used in i18n and message handling
    protected static final String
            homeKey = USSDSection.HOME.toString(),
            mtgKey = USSDSection.MEETINGS.toString(),
            userKey = USSDSection.USER_PROFILE.toString(),
            groupKey = USSDSection.GROUP_MANAGER.toString(),
            voteKey = USSDSection.VOTES.toString(),
            logKey = USSDSection.TODO.toString(),
            safetyKey = USSDSection.SAFETY_GROUP_MANAGER.toString(),
            moreKey = USSDSection.MORE.toString();
    protected static final String
            promptKey = "prompt",
            errorPromptKey = "prompt.error",
            optionsKey = "options.";

    protected static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
    protected static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE d MMM");
    protected static final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("d MMM");

    protected Request tooLongError = new Request("Error! Menu is too long.", new ArrayList<>());
    protected Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<>());

    private static final String openingMenuKey = String.join(".", Arrays.asList(homeKey, startMenu, optionsKey));

    private static final Map<USSDSection, String[]> openingMenuOptions = Collections.unmodifiableMap(Stream.of(
            new AbstractMap.SimpleEntry<>(MEETINGS, new String[]{meetingMenus + startMenu, openingMenuKey + mtgKey}),
            new AbstractMap.SimpleEntry<>(VOTES, new String[]{voteMenus + startMenu, openingMenuKey + voteKey}),
            new AbstractMap.SimpleEntry<>(TODO, new String[]{todoMenus + startMenu, openingMenuKey + logKey}),
            new AbstractMap.SimpleEntry<>(GROUP_MANAGER, new String[]{groupMenus + startMenu, openingMenuKey + groupKey}),
            new AbstractMap.SimpleEntry<>(USER_PROFILE, new String[]{userMenus + startMenu, openingMenuKey + userKey}),
            new AbstractMap.SimpleEntry<>(MORE, new String[]{moreMenus + startMenu, openingMenuKey + moreKey})).
            //new SimpleEntry<>(SAFETY_GROUP_MANAGER, new String[]{safetyMenus + startMenu, openingMenuKey + safetyKey})).
                    collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    private static final List<USSDSection> openingSequenceWithGroups = Arrays.asList(
            MEETINGS, VOTES, TODO, GROUP_MANAGER, USER_PROFILE, MORE);

    /*
    Methods that form the menu objects
     */

    protected Request menuBuilder(USSDMenu ussdMenu) throws URISyntaxException {
        return ussdMenuUtil.menuBuilder(ussdMenu, false);
    }

    protected Request menuBuilder(USSDMenu ussdMenu, boolean isFirstMenu) throws URISyntaxException {
        return ussdMenuUtil.menuBuilder(ussdMenu, isFirstMenu);
    }

    protected USSDMenu welcomeMenu(String opening, User user) {
        USSDMenu homeMenu = new USSDMenu(opening);
        openingSequenceWithGroups.forEach(s -> {
            String[] urlMsgPair = openingMenuOptions.get(s);
            homeMenu.addMenuOption(urlMsgPair[0], getMessage(urlMsgPair[1], user));
        });
        return homeMenu;
    }

    /*
    Method for experiment tracking
     */
    protected void recordExperimentResult(final String userUid, final String response) {
        Map<String, Object> tags = new HashMap<>();
        tags.put("revenue", 1);
        tags.put("meeting_response", 1);
        tags.put("content", response);
        experimentBroker.recordEvent("meeting_response", userUid, null, tags);
    }

    /**
     * Some default menu returns and some frequently used sets of menu options
     */

    protected Map<String, String> optionsHomeExit(User user, boolean shortForm) {
        return ImmutableMap.<String, String>builder().
                put("start_force", getMessage(startMenu + (shortForm ? ".short" : ""), user)).
                put("exit", getMessage("exit.option" + (shortForm ? ".short" : ""), user)).build();
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

    protected String getMessage(USSDSection section, String menu, String messageType, User user) {
        return messageAssembler.getMessage(section, menu, messageType, user);
    }

    // convenience function for when passing just a name (of user or group, for example)
    protected String getMessage(USSDSection section, String menuKey, String messageLocation, String parameter, User sessionUser) {
        return messageAssembler.getMessage(section, menuKey, messageLocation, parameter, sessionUser);
    }

    protected String getMessage(USSDSection section, String menu, String messageType, String[] parameters, User user) {
        return messageAssembler.getMessage(section, menu, messageType, parameters, user);
    }

    // for convenience, sometimes easier to read this way than passing around user instance
    protected String getMessage(String section, String menuKey, String messageLocation, Locale sessionLocale) {
        return messageAssembler.getMessage(section, menuKey, messageLocation, sessionLocale);
    }

    // final convenience version, for the root strings, stripping out "."
    protected String getMessage(String messageKey, User sessionUser) {
        return messageAssembler.getMessage(messageKey, sessionUser);
    }

    protected String getMessage(String messageKey, String language) {
        return messageAssembler.getMessage(messageKey, language);
    }

}