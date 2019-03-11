package za.org.grassroot.webapp.controller.ussd;

import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignTextBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Option;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignConstants;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.startMenu;
import static za.org.grassroot.webapp.enums.USSDSection.HOME;

@Service
public class UssdHomeServiceImpl implements UssdHomeService {
	private final Logger log = LoggerFactory.getLogger(UssdHomeServiceImpl.class);

	@Value("${grassroot.ussd.code.length:9}")
	private int hashPosition = 9;

	@Value("${grassroot.ussd.safety.suffix:911}")
	private String safetyCode = "911";

	@Value("${grassroot.ussd.livewire.suffix:411}")
	private String livewireSuffix = "411";

	@Value("${grassroot.ussd.sendlink.suffix:123}")
	private String sendMeLink = "123";

	@Value("${grassroot.geo.apis.enabled:false}")
	private boolean geoApisEnabled = false;

	private Map<String, String> geoApiSuffixes;

	private final UssdLiveWireService ussdLiveWireService;
	private final UssdGeoApiService ussdGeoApiService;
	private final UssdTodoService ussdTodoService;
	private final UssdSafetyGroupService ussdSafetyGroupService;
	private final UssdVoteService ussdVoteService;
	private final UssdMeetingService ussdMeetingService;
	private final UserManagementService userManager;
	private final CacheUtilService cacheManager;

	private final LocationInfoBroker locationInfoBroker;
	private final CampaignBroker campaignBroker;
	private final CampaignTextBroker campaignTextBroker;
	private final AsyncUserLogger userLogger;
	private final UssdSupport ussdSupport;
	private final UserResponseBroker userResponseBroker;

	private final GroupQueryBroker groupQueryBroker;
	private final AccountFeaturesBroker accountFeaturesBroker;
	private final GroupBroker groupBroker;

	private static final USSDSection thisSection = HOME;

	public UssdHomeServiceImpl(UssdSupport ussdSupport, UssdLiveWireService ussdLiveWireService, @Autowired(required = false) UssdGeoApiService ussdGeoApiService, UssdTodoService ussdTodoService, UssdVoteService ussdVoteService, UssdMeetingService ussdMeetingService,
							   UssdSafetyGroupService ussdSafetyGroupService, LocationInfoBroker locationInfoBroker, UserManagementService userManager, CampaignBroker campaignBroker, CampaignTextBroker campaignTextBroker, AsyncUserLogger userLogger,
							   CacheUtilService cacheManager, UserResponseBroker userResponseBroker, GroupQueryBroker groupQueryBroker, AccountFeaturesBroker accountFeaturesBroker, GroupBroker groupBroker) {
		this.ussdLiveWireService = ussdLiveWireService;
		this.ussdSafetyGroupService = ussdSafetyGroupService;
		this.locationInfoBroker = locationInfoBroker;
		this.ussdGeoApiService = ussdGeoApiService;
		this.userManager = userManager;
		this.campaignBroker = campaignBroker;
		this.campaignTextBroker = campaignTextBroker;
		this.userLogger = userLogger;
		this.ussdSupport = ussdSupport;
		this.cacheManager = cacheManager;
		this.ussdTodoService = ussdTodoService;
		this.ussdVoteService = ussdVoteService;
		this.ussdMeetingService = ussdMeetingService;
		this.userResponseBroker = userResponseBroker;
		this.groupQueryBroker = groupQueryBroker;
		this.accountFeaturesBroker = accountFeaturesBroker;
		this.groupBroker = groupBroker;
	}

	@PostConstruct
	public void init() {
		if (locationInfoBroker != null) {
			log.info("Initiating USSD, setting geo apis");
			geoApiSuffixes = locationInfoBroker.getAvailableSuffixes();
			log.info("Set geo api suffixes: {}", geoApiSuffixes);
		} else {
			log.info("Geo APIs disabled, not setting");
		}
	}

	@Override
	@Transactional
	public Request processStartMenu(String inputNumber, String enteredUSSD) throws URISyntaxException {
		final Stopwatch stopwatch = Stopwatch.createStarted();

		log.info("entered USSD = {}, hashPosition = {}", enteredUSSD, hashPosition);
		final Optional<String> trailingDigits = parseTrailingDigits(enteredUSSD);
		log.debug("Initiating USSD, trailing digits present: {}", trailingDigits.isPresent());

		if (!trailingDigits.isPresent() && userInterrupted(inputNumber)) {
			USSDMenu ussdMenu = interruptedPrompt(inputNumber, null);
			return ussdSupport.menuBuilder(ussdMenu);
		} else {
			final User sessionUser = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
			userLogger.recordUserSession(sessionUser.getUid(), UserInterfaceType.USSD);

			final USSDMenu openingMenu = trailingDigits.isPresent() ?
					handleTrailingDigits(trailingDigits.get(), inputNumber, sessionUser) :
					checkForResponseOrDefault(sessionUser);

			log.info("Generating home menu, time taken: {} msecs", stopwatch.elapsed(TimeUnit.MILLISECONDS));
			return ussdSupport.menuBuilder(openingMenu, true);
		}
	}

	private Optional<String> parseTrailingDigits(String enteredUSSD) {
		if (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1) {
			final String digits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
			return Optional.of(digits);
		}
		return Optional.empty();
	}

	private USSDMenu directBasedOnTrailingDigits(final String trailingDigits, final User user) {
		USSDMenu returnMenu;
		log.debug("Processing trailing digits ..." + trailingDigits);
		boolean sendWelcomeIfNew = false;
		if (safetyCode.equals(trailingDigits)) {
			returnMenu = ussdSafetyGroupService.assemblePanicButtonActivationMenu(user);
		} else if (livewireSuffix.equals(trailingDigits)) {
			returnMenu = ussdLiveWireService.assembleLiveWireOpening(user, 0);
			sendWelcomeIfNew = true;
		} else if (sendMeLink.equals(trailingDigits)) {
			returnMenu = assembleSendMeAndroidLinkMenu(user);
			sendWelcomeIfNew = true;
		} else if (geoApisEnabled && geoApiSuffixes.keySet().contains(trailingDigits)) {
			returnMenu = ussdGeoApiService.openingMenu(ussdSupport.convert(user), geoApiSuffixes.get(trailingDigits));
		} else {
			returnMenu = joinGroupViaToken(user, trailingDigits);
			if (returnMenu != null) { // if group joined
				sendWelcomeIfNew = true;
			} else {
				log.info("checking if campaign: {}", trailingDigits);
				returnMenu = getActiveCampaignForTrailingCode(trailingDigits, user);
			}
			log.info("group or campaign join, trailing digits ={}, send welcome = {}", trailingDigits, sendWelcomeIfNew);
		}
		recordInitiatedAndSendWelcome(user, sendWelcomeIfNew);
		return returnMenu;
	}

	private USSDMenu handleTrailingDigits(final String trailingDigits, final String inputNumber, User user) {
		return userInterrupted(inputNumber) && !safetyCode.equals(trailingDigits) ?
				interruptedPrompt(inputNumber, trailingDigits) :
				directBasedOnTrailingDigits(trailingDigits, user);
	}

	private USSDMenu checkForResponseOrDefault(final User user) throws URISyntaxException {
		recordInitiatedAndSendWelcome(user, true);
		EntityForUserResponse entity = userResponseBroker.checkForEntityForUserResponse(user.getUid(), true);
		USSDResponseTypes neededResponse = neededResponse(entity, user);
		return neededResponse.equals(USSDResponseTypes.NONE)
				? defaultStartMenu(user)
				: requestUserResponse(user, neededResponse, entity);
	}

	@Override
	@Transactional
	public Request processForceStartMenu(String inputNumber, String trailingDigits) throws URISyntaxException {
		final User user = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
		final USSDMenu ussdMenu = trailingDigits != null ? directBasedOnTrailingDigits(trailingDigits, user) : defaultStartMenu(user);
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional(readOnly = true)
	public Request processExitScreen(String inputNumber) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		String exitMessage = ussdSupport.getMessage("exit." + ussdSupport.promptKey, user);
		return ussdSupport.menuBuilder(new USSDMenu(exitMessage));
	}

	@Override
	public Request processNotBuilt(String inputNumber) throws URISyntaxException {
		String errorMessage = ussdSupport.getMessage("ussd.error", "en");
		return ussdSupport.menuBuilder(new USSDMenu(errorMessage, ussdSupport.optionsHomeExit(userManager.findByInputNumber(inputNumber), false)));
	}

	@Override
	public Request processTestQuestion() throws URISyntaxException {
		final Option option = new Option("Yes I can!", 1, 1, new URI("http://yourdomain.tld/ussdxml.ashx?file=2"), true);
		return new Request("Can you answer the question?", Collections.singletonList(option));
	}

	private void recordInitiatedAndSendWelcome(User user, boolean sendWelcome) {
		if (!user.isHasInitiatedSession()) {
			userManager.setHasInitiatedUssdSession(user, sendWelcome);
		}
	}

	/*
 Section of helper methods for opening menu response handling
  */
	private USSDMenu assembleSendMeAndroidLinkMenu(User user) {
		userManager.sendAndroidLinkSms(user.getUid());
		String message = ussdSupport.getMessage(thisSection, "link.android", ussdSupport.promptKey, user);
		return new USSDMenu(message, ussdSupport.optionsHomeExit(user, false));
	}

	private USSDMenu getActiveCampaignForTrailingCode(String trailingDigits, User user) {
		Campaign campaign = campaignBroker.getCampaignDetailsByCode(trailingDigits, user.getUid(), true, UserInterfaceType.USSD);
		log.info("found a campaign? : {}", campaign);
		return (campaign != null) ?
				assembleCampaignMessageResponse(campaign, user) :
				ussdSupport.welcomeMenu(ussdSupport.getMessage(HOME, startMenu, ussdSupport.promptKey + ".unknown.request", user), user);
	}

	private USSDMenu assembleCampaignMessageResponse(Campaign campaign, User user) {
		log.info("fire off SMS in background, if exists ...");
		campaignTextBroker.checkForAndTriggerCampaignText(campaign.getUid(), user.getUid(), null, UserInterfaceType.USSD);
		log.info("fired off ... continue ...");
		Set<Locale> supportedCampaignLanguages = campaignBroker.getCampaignLanguages(campaign.getUid());
		userLogger.recordUserLog(user.getUid(), UserLogType.CAMPAIGN_ENGAGED, campaign.getUid(), UserInterfaceType.USSD);
		if (supportedCampaignLanguages.size() == 1) {
			return assembleCampaignResponse(campaign, supportedCampaignLanguages.iterator().next());
		} else if (!StringUtils.isEmpty(user.getLanguageCode()) && supportedCampaignLanguages.contains(new Locale(user.getLanguageCode()))) {
			return assembleCampaignResponse(campaign, user.getLocale());
		} else {
			return assembleCampaignResponseForSupportedLanguage(campaign, user);
		}
	}

	private USSDMenu assembleCampaignResponse(Campaign campaign, Locale userLocale) {
		CampaignMessage campaignMessage = campaignBroker.getOpeningMessage(campaign.getUid(), userLocale, UserInterfaceType.USSD, null);
		String promptMessage = campaignMessage.getMessage();

		Map<String, String> linksMap = new HashMap<>();
		if (campaignMessage.getNextMessages() != null && !campaignMessage.getNextMessages().isEmpty()) {
			for (Map.Entry<String, CampaignActionType> action : campaignMessage.getNextMessages().entrySet()) {
				String optionKey = USSDCampaignConstants.CAMPAIGN_PREFIX + action.getValue().name().toLowerCase();
				String option = ussdSupport.getMessage(optionKey, userLocale.getLanguage());
				StringBuilder url = new StringBuilder("campaign/");
				url.append(USSDCampaignConstants.getCampaignUrlPrefixs().get(action.getValue())).append("?");
				url.append(USSDCampaignConstants.MESSAGE_UID_PARAMETER).append(action.getKey());
				log.debug("adding url: {}", url.toString());
				linksMap.put(url.toString(), option);
			}
		}
		return new USSDMenu(promptMessage, linksMap);
	}

	private USSDMenu assembleCampaignResponseForSupportedLanguage(Campaign campaign, User user) {
		String promptMessage = ussdSupport.getMessage("user.language.prompt", user.getLocale().getLanguage());
		Map<String, String> linksMap = new HashMap<>();
		Set<Locale> localeSet = campaignBroker.getCampaignLanguages(campaign.getUid());
		for (Locale locale : localeSet) {
			String option = ussdSupport.getMessage("language." + locale.getLanguage(), user.getLocale().getLanguage());
			String url = "campaign/set-lang?campaignUid=" + campaign.getUid() +
					USSDCampaignConstants.LANG_SUFFIX + locale.getLanguage();
			linksMap.put(url, option);
		}
		return new USSDMenu(promptMessage, linksMap);
	}

	private USSDMenu interruptedPrompt(String inputNumber, String trailingDigits) {
		String returnUrl = cacheManager.fetchUssdMenuForUser(inputNumber);
		log.info("The user was interrupted somewhere: trailing digits: {}, URL: {}", trailingDigits, returnUrl);

		User user = userManager.findByInputNumber(inputNumber);
		USSDMenu promptMenu = new USSDMenu(ussdSupport.getMessage(thisSection, startMenu, ussdSupport.promptKey + "-interrupted", user));
		promptMenu.addMenuOption(returnUrl, ussdSupport.getMessage(thisSection, startMenu, "interrupted.resume", user));

		final String startMenuOption = startMenu + "_force" + (!StringUtils.isEmpty(trailingDigits) ? "?trailingDigits=" + trailingDigits : "");
		log.info("User interrupted, start menu option: {}", startMenuOption);
		promptMenu.addMenuOption(startMenuOption, ussdSupport.getMessage(thisSection, startMenu, "interrupted.start", user));

		// set the user's "last USSD menu" back to null, so avoids them always coming back here
		userLogger.recordUssdInterruption(user.getUid(), returnUrl);
		cacheManager.clearUssdMenuForUser(inputNumber);

		return promptMenu;
	}

	private boolean userInterrupted(String inputNumber) {
		return (cacheManager.fetchUssdMenuForUser(inputNumber) != null);
	}

	private USSDResponseTypes neededResponse(EntityForUserResponse userResponse, User user) {
		return userResponse != null ? USSDResponseTypes.fromJpaEntityType(userResponse.getJpaEntityType()) :
				userManager.needsToSetName(user, false) ? USSDResponseTypes.RENAME_SELF : USSDResponseTypes.NONE;
	}

	private USSDMenu defaultStartMenu(User sessionUser) {
		String welcomeMessage = sessionUser.hasName() ?
				ussdSupport.getMessage(thisSection, startMenu, ussdSupport.promptKey + "-named", sessionUser.getName(""), sessionUser) :
				ussdSupport.getMessage(thisSection, startMenu, ussdSupport.promptKey, sessionUser);
		return ussdSupport.welcomeMenu(welcomeMessage, sessionUser);
	}

	private USSDMenu requestUserResponse(User user, USSDResponseTypes response, EntityForUserResponse entity) throws URISyntaxException {
		switch (response) {
			case RESPOND_SAFETY:
				return ussdSafetyGroupService.assemblePanicButtonActivationResponse(user, (SafetyEvent) entity);
			case VOTE:
				return ussdVoteService.assembleVoteMenu(user, (Vote) entity);
			case MTG_RSVP:
				return ussdMeetingService.assembleRsvpMenu(user, (Event) entity);
			case RESPOND_TODO:
				return ussdTodoService.respondToTodo(user, (Todo) entity);
			case RENAME_SELF:
				return new USSDMenu(ussdSupport.getMessage(thisSection, startMenu, ussdSupport.promptKey + "-rename", user),
						"rename-start");
			default:
				return defaultStartMenu(user);
		}
	}

	private USSDMenu joinGroupViaToken(final User user, final String trailingDigits) {
		final String token = trailingDigits.trim();
		final Optional<Group> searchResult = groupQueryBroker.findGroupFromJoinCode(token);
		if (searchResult.isPresent()) {
			final Group group = searchResult.get();
			log.debug("adding user via join code ... {}", token);
			boolean groupLimitReached = accountFeaturesBroker.numberMembersLeftForGroup(group, GroupJoinMethod.USSD_JOIN_CODE) == 0;
			if (groupLimitReached) {
				return notifyGroupLimitReached(user, group);
			} else {
				final Membership membership = groupBroker.addMemberViaJoinCode(user, group, token, UserInterfaceType.USSD);
				final Optional<USSDMenu> massVoteMenu = ussdVoteService.processPossibleMassVote(user, group);
				if (massVoteMenu.isPresent()) {
					return massVoteMenu.get();
				} if (!group.getJoinTopics().isEmpty() && !membership.hasAnyTopic(group.getJoinTopics())) {
					return askForJoinTopics(group, user);
				} else {
					String promptStart = group.hasName() ? ussdSupport.getMessage(HOME, startMenu, ussdSupport.promptKey + ".group.token.named", group.getGroupName(), user) :
							ussdSupport.getMessage(HOME, startMenu, ussdSupport.promptKey + ".group.token.unnamed", user);
					return ussdSupport.setUserProfile(user, promptStart);
				}
			}
		} else {
			return null;
		}
	}

	private USSDMenu notifyGroupLimitReached(User user, Group group) {
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage("group.join.limit.exceeded", new String[]{group.getName()}, user));
		menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
		return menu;
	}

	private USSDMenu askForJoinTopics(Group group, User user) {
		final String prompt = ussdSupport.getMessage(HOME, startMenu, ussdSupport.promptKey + ".group.topics", group.getName(), user);
		final String urlBase = "group/join/topics?groupUid=" + group.getUid() + "&topic=";
		USSDMenu menu = new USSDMenu(prompt);
		group.getJoinTopics().forEach(topic -> menu.addMenuOption(urlBase + USSDUrlUtil.encodeParameter(topic), topic));
		return menu;
	}

}
