package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.integration.experiments.ExperimentBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDMenuUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static za.org.grassroot.webapp.enums.USSDSection.GROUP_MANAGER;
import static za.org.grassroot.webapp.enums.USSDSection.MEETINGS;
import static za.org.grassroot.webapp.enums.USSDSection.MORE;
import static za.org.grassroot.webapp.enums.USSDSection.TODO;
import static za.org.grassroot.webapp.enums.USSDSection.USER_PROFILE;
import static za.org.grassroot.webapp.enums.USSDSection.VOTES;

@Component
@Slf4j
public class UssdSupport {
	@Value("${grassroot.languages.ussd.minsessions:2}")
	public int preLanguageSessions;

	@Autowired
	private ExperimentBroker experimentBroker;

	@Autowired
	public UserManagementService userManager;

	@Autowired
	public AsyncUserLogger userLogger;

	@Autowired
	public CacheUtilService cacheManager;

	@Autowired
	public USSDMessageAssembler messageAssembler;
	@Autowired
	public USSDMenuUtil ussdMenuUtil;

	/**
	 * SECTION: Constants used throughout the code
	 */

	// Constants used in URL mapping and message handling
	public static final String homePath = USSDUrlUtil.homePath;
	public static final String meetingMenus = "mtg/", userMenus = "user/", groupMenus = "group/", voteMenus = "vote/", todoMenus = "todo/", safetyMenus = "safety/", moreMenus = "more/", U404 = "error";
	// referencing these from the Util class so can be common across tests etc, but stating here so not cumbersome in sub-classes
	public static final String phoneNumber = USSDUrlUtil.phoneNumber, userInputParam = USSDUrlUtil.userInputParam, groupUidParam = USSDUrlUtil.groupUidParam, entityUidParam = USSDUrlUtil.entityUidParam, previousMenu = USSDUrlUtil.previousMenu, yesOrNoParam = USSDUrlUtil.yesOrNoParam, interruptedFlag = USSDUrlUtil.interruptedFlag, interruptedInput = USSDUrlUtil.interruptedInput, revisingFlag = USSDUrlUtil.revisingFlag;
	public static final String startMenu = "start", groupUidUrlSuffix = USSDUrlUtil.groupUidUrlSuffix, entityUidUrlSuffix = USSDUrlUtil.entityUidUrlSuffix, doSuffix = "-do";
	// Constants used in i18n and message handling
	public static final String homeKey = USSDSection.HOME.toString(), mtgKey = USSDSection.MEETINGS
			.toString(), userKey = USSDSection.USER_PROFILE.toString(), groupKey = USSDSection.GROUP_MANAGER
			.toString(), voteKey = USSDSection.VOTES.toString(), logKey = USSDSection.TODO
			.toString(), safetyKey = USSDSection.SAFETY_GROUP_MANAGER.toString(), moreKey = USSDSection.MORE.toString();
	public static final String promptKey = "prompt", errorPromptKey = "prompt.error", optionsKey = "options.";

	public static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
	public static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE d MMM");
	public static final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("d MMM");

	public Request tooLongError = new Request("Error! Menu is too long.", new ArrayList<>());
	public Request noUserError = new Request("Error! Couldn't find you as a user.", new ArrayList<>());

	private static final String openingMenuKey = String.join(".", Arrays.asList(homeKey, startMenu, optionsKey));

	private static final Map<USSDSection, String[]> openingMenuOptions = Collections.unmodifiableMap(
			Stream.of(new AbstractMap.SimpleEntry<>(MEETINGS, new String[]{meetingMenus + startMenu, openingMenuKey + mtgKey}),
					new AbstractMap.SimpleEntry<>(VOTES, new String[]{voteMenus + startMenu, openingMenuKey + voteKey}),
					new AbstractMap.SimpleEntry<>(TODO, new String[]{todoMenus + startMenu, openingMenuKey + logKey}),
					new AbstractMap.SimpleEntry<>(GROUP_MANAGER, new String[]{groupMenus + startMenu, openingMenuKey + groupKey}),
					new AbstractMap.SimpleEntry<>(USER_PROFILE, new String[]{userMenus + startMenu, openingMenuKey + userKey}),
					new AbstractMap.SimpleEntry<>(MORE, new String[]{moreMenus + startMenu, openingMenuKey + moreKey})).
					//new SimpleEntry<>(SAFETY_GROUP_MANAGER, new String[]{safetyMenus + startMenu, openingMenuKey + safetyKey})).
							collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

	private static final List<USSDSection> openingSequenceWithGroups = Arrays
			.asList(MEETINGS, VOTES, TODO, GROUP_MANAGER, USER_PROFILE, MORE);

 /*
 Methods that form the menu objects
  */

	public Request menuBuilder(USSDMenu ussdMenu) throws URISyntaxException {
		return ussdMenuUtil.menuBuilder(ussdMenu, false);
	}

	public Request menuBuilder(USSDMenu ussdMenu, boolean isFirstMenu) throws URISyntaxException {
		return ussdMenuUtil.menuBuilder(ussdMenu, isFirstMenu);
	}

	public USSDMenu welcomeMenu(String opening, User user) {
		USSDMenu homeMenu = new USSDMenu(opening);
		openingSequenceWithGroups.forEach(s -> {
			String[] urlMsgPair = openingMenuOptions.get(s);
			homeMenu.addMenuOption(urlMsgPair[0], getMessage(urlMsgPair[1], user));
		});
		return homeMenu;
	}

	public USSDMenu promptLanguageMenu(User user) {
		return new USSDMenu(messageAssembler.getMessage("language.prompt", user), languageOptions(userMenus + "language-do?language="));
	}

	/*
	Method for experiment tracking
	 */
	public void recordExperimentResult(final String userUid, final String response) {
		Map<String, Object> tags = new HashMap<>();
		tags.put("revenue", 1);
		tags.put("meeting_response", 1);
		tags.put("content", response);
		experimentBroker.recordEvent("meeting_response", userUid, null, tags);
	}

	/**
	 * Some default menu returns and some frequently used sets of menu options
	 */
	public Map<String, String> optionsHomeExit(UserMinimalProjection user, boolean shortForm) {
		return ImmutableMap.<String, String>builder().
				put("start_force", getMessage(startMenu + (shortForm ? ".short" : ""), user)).
				put("exit", getMessage("exit.option" + (shortForm ? ".short" : ""), user)).build();
	}

	public Map<String, String> optionsHomeExit(User user, boolean shortForm) {
		return optionsHomeExit(convert(user), shortForm);
	}

	public Map<String, String> optionsYesNo(User sessionUser, String yesUri, String noUri) {
		return optionsYesNo(convert(sessionUser), yesUri, noUri);
	}

	public Map<String, String> optionsYesNo(UserMinimalProjection user, String yesUri, String noUri) {
		return ImmutableMap.<String, String>builder().
				put(yesUri + "&" + yesOrNoParam + "=yes", getMessage(optionsKey + "yes", user)).
				put(noUri + "&" + yesOrNoParam + "=no", getMessage(optionsKey + "no", user)).build();
	}

	public Map<String, String> optionsYesNo(User sesionUser, String nextUri) {
		return optionsYesNo(sesionUser, nextUri, nextUri);
	}

	public Map<String, String> provinceOptions(User user, String url) {
		return provinceOptions(convert(user), url);
	}

	public Map<String, String> provinceOptions(UserMinimalProjection user, String url) {
		Map<String, String> options = new LinkedHashMap<>();
		Province.ZA_CANONICAL.forEach(p -> options.put(url + p, getMessage("province." + p.name().substring("ZA_".length()), user)));
		return options;
	}

	public Map<String, String> languageOptions(String url) {
		Map<String, String> options = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : BaseController.getImplementedLanguages().entrySet()) {
			options.put(url + entry.getKey(), entry.getValue());
		}
		return options;
	}

 /*
 i18n helper methods
  */

	public String getMessage(USSDSection section, String menu, String messageType, UserMinimalProjection user) {
		return messageAssembler.getMessage(section, menu, messageType, user);
	}

	// convenience function for when passing just a name (of user or group, for example)
	public String getMessage(USSDSection section, String menuKey, String messageLocation, String parameter, UserMinimalProjection user) {
		return messageAssembler.getMessage(section, menuKey, messageLocation, parameter, user);
	}

	public String getMessage(USSDSection section, String menu, String messageType, String[] parameters, User user) {
		return messageAssembler.getMessage(section, menu, messageType, parameters, user);
	}

	// for convenience, sometimes easier to read this way than passing around user instance
	public String getMessage(String section, String menuKey, String messageLocation, Locale sessionLocale) {
		return messageAssembler.getMessage(section, menuKey, messageLocation, sessionLocale);
	}

	// final convenience version, for the root strings, stripping out "."
	public String getMessage(String messageKey, UserMinimalProjection user) {
		return messageAssembler.getMessage(messageKey, user);
	}

	public String getMessage(String messageKey, String language) {
		return messageAssembler.getMessage(messageKey, language);
	}

	public String getMessage(String messageKey, String[] params, User user) {
		return messageAssembler.getMessage(messageKey, params, user);
	}

	/*
	For some overloads as we work on this conversion
	 */
	public UserMinimalProjection convert(User user) {
		return new UserMinimalProjection(user.getUid(), user.getDisplayName(), user.getLanguageCode(), user.getProvince());
	}

	public String getMessage(USSDSection section, String menu, String messageType, User user) {
		return getMessage(section, menu, messageType, convert(user));
	}

	public String getMessage(String messageKey, User sessionUser) {
		return getMessage(messageKey, convert(sessionUser));
	}

	public String getMessage(USSDSection section, String menuKey, String messageLocation, String parameter, User sessionUser) {
		return getMessage(section, menuKey, messageLocation, parameter, convert(sessionUser));
	}

}
