package za.org.grassroot.webapp.controller.ussd;

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
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.campaign.CampaignTextBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.group.USSDGroupJoinController;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDResponseTypes;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDCampaignConstants;

import javax.annotation.PostConstruct;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static za.org.grassroot.webapp.enums.USSDSection.HOME;

@Service
public class UssdServiceImpl implements UssdService {
	private final Logger log = LoggerFactory.getLogger(UssdServiceImpl.class);

	@Value("${grassroot.ussd.code.length:9}")
	private int hashPosition;

	@Value("${grassroot.ussd.safety.suffix:911}")
	private String safetyCode;

	@Value("${grassroot.ussd.livewire.suffix:411}")
	private String livewireSuffix;

	@Value("${grassroot.ussd.sendlink.suffix:123}")
	private String sendMeLink;

	@Value("${grassroot.geo.apis.enabled:false}")
	private boolean geoApisEnabled;

	private Map<String, String> geoApiSuffixes;

	// since this controller in effect routes responses, needs access to the other primary ones
	// setters are for testing (since we need this controller in the tests of the handler)
	private final USSDLiveWireController liveWireController;
	private final USSDSafetyGroupController safetyController;
	private final LocationInfoBroker locationInfoBroker;
	private final USSDGeoApiController geoApiController;
	private final USSDGroupJoinController groupJoinController;
	private final UserManagementService userManager;
	private final CampaignBroker campaignBroker;
	private final CampaignTextBroker campaignTextBroker;
	private final AsyncUserLogger userLogger;
	private final UssdSupport ussdSupport;
	private final CacheUtilService cacheManager;
	private final USSDTodoController todoController;
	private final USSDVoteController voteController;
	private final USSDMeetingController meetingController;
	private final UserResponseBroker userResponseBroker;

	private static final USSDSection thisSection = HOME;


	public UssdServiceImpl(@Autowired(required = false) USSDGeoApiController geoApiController, USSDLiveWireController liveWireController, USSDSafetyGroupController safetyController, LocationInfoBroker locationInfoBroker, USSDGroupJoinController groupJoinController, UserManagementService userManager, CampaignBroker campaignBroker, CampaignTextBroker campaignTextBroker, AsyncUserLogger userLogger, UssdSupport ussdSupport, CacheUtilService cacheManager, USSDTodoController todoController, USSDVoteController voteController, USSDMeetingController meetingController, UserResponseBroker userResponseBroker) {
		this.liveWireController = liveWireController;
		this.safetyController = safetyController;
		this.locationInfoBroker = locationInfoBroker;
		this.geoApiController = geoApiController;
		this.groupJoinController = groupJoinController;
		this.userManager = userManager;
		this.campaignBroker = campaignBroker;
		this.campaignTextBroker = campaignTextBroker;
		this.userLogger = userLogger;
		this.ussdSupport = ussdSupport;
		this.cacheManager = cacheManager;
		this.todoController = todoController;
		this.voteController = voteController;
		this.meetingController = meetingController;
		this.userResponseBroker = userResponseBroker;
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
		Long startTime = System.currentTimeMillis();

		final boolean trailingDigitsPresent = codeHasTrailingDigits(enteredUSSD);
		log.debug("Initiating USSD, trailing digits present: {}", trailingDigitsPresent);

		if (!trailingDigitsPresent && userInterrupted(inputNumber)) {
			return ussdSupport.menuBuilder(interruptedPrompt(inputNumber, null));
		}

		final User sessionUser = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
		userLogger.recordUserSession(sessionUser.getUid(), UserInterfaceType.USSD);

		USSDMenu openingMenu = trailingDigitsPresent ?
				handleTrailingDigits(enteredUSSD, inputNumber, sessionUser) :
				checkForResponseOrDefault(sessionUser);

		Long endTime = System.currentTimeMillis();
		log.info(String.format("Generating home menu, time taken: %d msecs", endTime - startTime));
		return ussdSupport.menuBuilder(openingMenu, true);
	}


	private USSDMenu directBasedOnTrailingDigits(final String trailingDigits, final User user) {
		USSDMenu returnMenu;
		log.info("Processing trailing digits ..." + trailingDigits);
		boolean sendWelcomeIfNew = false;
		if (safetyCode.equals(trailingDigits)) {
			returnMenu = safetyController.assemblePanicButtonActivationMenu(user);
		} else if (livewireSuffix.equals(trailingDigits)) {
			returnMenu = liveWireController.assembleLiveWireOpening(user, 0);
			sendWelcomeIfNew = true;
		} else if (sendMeLink.equals(trailingDigits)) {
			returnMenu = assembleSendMeAndroidLinkMenu(user);
			sendWelcomeIfNew = true;
		} else if (geoApisEnabled && geoApiSuffixes.keySet().contains(trailingDigits)) {
			returnMenu = geoApiController.openingMenu(ussdSupport.convert(user), geoApiSuffixes.get(trailingDigits));
			sendWelcomeIfNew = false;
		} else {
			returnMenu = groupJoinController.ussdJoinGroupViaToken(user, trailingDigits);
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

	private USSDMenu handleTrailingDigits(final String enteredUSSD, final String inputNumber, User user) {
		String trailingDigits = enteredUSSD.substring(hashPosition + 1, enteredUSSD.length() - 1);
		return userInterrupted(inputNumber) && !safetyCode.equals(trailingDigits) ?
				interruptedPrompt(inputNumber, trailingDigits) : directBasedOnTrailingDigits(trailingDigits, user);
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
		return ussdSupport.menuBuilder(trailingDigits != null ? directBasedOnTrailingDigits(trailingDigits, user) : defaultStartMenu(user));
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
				ussdSupport.welcomeMenu(ussdSupport.getMessage(HOME, ussdSupport.startMenu, ussdSupport.promptKey + ".unknown.request", user), user);
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
		USSDMenu promptMenu = new USSDMenu(ussdSupport.getMessage(thisSection, ussdSupport.startMenu, ussdSupport.promptKey + "-interrupted", user));
		promptMenu.addMenuOption(returnUrl, ussdSupport.getMessage(thisSection, ussdSupport.startMenu, "interrupted.resume", user));

		final String startMenuOption = ussdSupport.startMenu + "_force" + (!StringUtils.isEmpty(trailingDigits) ? "?trailingDigits=" + trailingDigits : "");
		log.info("User interrupted, start menu option: {}", startMenuOption);
		promptMenu.addMenuOption(startMenuOption, ussdSupport.getMessage(thisSection, ussdSupport.startMenu, "interrupted.start", user));

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

	private boolean codeHasTrailingDigits(String enteredUSSD) {
		log.info("entered USSD = {}, hashPosition = {}", enteredUSSD, hashPosition);
		return (enteredUSSD != null && enteredUSSD.length() > hashPosition + 1);
	}

	private USSDMenu defaultStartMenu(User sessionUser) {
		String welcomeMessage = sessionUser.hasName() ?
				ussdSupport.getMessage(thisSection, ussdSupport.startMenu, ussdSupport.promptKey + "-named", sessionUser.getName(""), sessionUser) :
				ussdSupport.getMessage(thisSection, ussdSupport.startMenu, ussdSupport.promptKey, sessionUser);
		return ussdSupport.welcomeMenu(welcomeMessage, sessionUser);
	}

	private USSDMenu requestUserResponse(User user, USSDResponseTypes response, EntityForUserResponse entity) throws URISyntaxException {
		switch (response) {
			case RESPOND_SAFETY:
				return safetyController.assemblePanicButtonActivationResponse(user, (SafetyEvent) entity);
			case VOTE:
				return voteController.assembleVoteMenu(user, entity);
			case MTG_RSVP:
				return meetingController.assembleRsvpMenu(user, entity);
			case RESPOND_TODO:
				return todoController.respondToTodo(user, entity);
			case RENAME_SELF:
				return new USSDMenu(ussdSupport.getMessage(thisSection, USSDBaseController.startMenu, ussdSupport.promptKey + "-rename", user),
						"rename-start");
			default:
				return defaultStartMenu(user);
		}
	}

}
