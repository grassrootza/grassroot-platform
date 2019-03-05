package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventRequest;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventRequestBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.enums.VoteTime;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.entityUidUrlSuffix;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;
import static za.org.grassroot.webapp.enums.VoteTime.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

@Service @Slf4j
public class UssdVoteServiceImpl implements UssdVoteService {
	private static final int EVENT_LIMIT_WARNING_THRESHOLD = 5; // only warn when below this

	@Value("${grassroot.languages.ussd.minsessions:2}")
	private int preLanguageSessions;

	private final EventBroker eventBroker;
	private final UssdSupport ussdSupport;
	private final VoteBroker voteBroker;
	private final UserManagementService userManager;
	private final CacheUtilService cacheManager;
	private final PermissionBroker permissionBroker;
	private final USSDEventUtil eventUtil;
	private final USSDGroupUtil groupUtil;
	private final EventRequestBroker eventRequestBroker;
	private final AsyncUserLogger userLogger;
	private final AccountFeaturesBroker accountFeaturesBroker;

	private static final USSDSection thisSection = USSDSection.VOTES;

	public UssdVoteServiceImpl(EventBroker eventBroker, UssdSupport ussdSupport, VoteBroker voteBroker, UserManagementService userManager, CacheUtilService cacheManager, PermissionBroker permissionBroker, USSDEventUtil eventUtil, USSDGroupUtil groupUtil, EventRequestBroker eventRequestBroker, AsyncUserLogger userLogger, AccountFeaturesBroker accountFeaturesBroker) {
		this.eventBroker = eventBroker;
		this.ussdSupport = ussdSupport;
		this.voteBroker = voteBroker;
		this.userManager = userManager;
		this.cacheManager = cacheManager;
		this.permissionBroker = permissionBroker;
		this.eventUtil = eventUtil;
		this.groupUtil = groupUtil;
		this.eventRequestBroker = eventRequestBroker;
		this.userLogger = userLogger;
		this.accountFeaturesBroker = accountFeaturesBroker;
	}

	@Override
	public USSDMenu assembleVoteMenu(User user, Vote vote) {
		log.info("Processing vote: ", vote);

		if (EventSpecialForm.MASS_VOTE.equals(vote.getSpecialForm()))
			return processMassVoteOpening(vote, user);

		final String[] promptFields = new String[]{vote.getAncestorGroup().getName(""),
				vote.getCreatedByUser().getMembership(vote.getAncestorGroup()).getDisplayName(),
				vote.getName()};

		final String prompt = EventSpecialForm.MASS_VOTE.equals(vote.getSpecialForm()) ? promptKey + "-vote-mass" : promptKey + "-vote";
		USSDMenu openingMenu = new USSDMenu(ussdSupport.getMessage(USSDSection.HOME, UssdSupport.startMenu, prompt, promptFields, user));

		if (vote.getVoteOptions().isEmpty()) {
			addYesNoOptions(vote, user, openingMenu);
		} else {
			addVoteOptions(vote, openingMenu);
		}

		if (!StringUtils.isEmpty(vote.getDescription())) {
			openingMenu.addMenuOption(voteMenus + "description?voteUid=" + vote.getUid() + "&back=respond",
					ussdSupport.getMessage("home.generic.moreinfo", user));
		}

		return openingMenu;
	}

	@Override
	@Transactional
	public Request processShowVoteDescription(String inputNumber, String voteUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Vote vote = voteBroker.load(voteUid);

		USSDMenu menu = new USSDMenu(vote.getDescription());
		if (vote.getVoteOptions().isEmpty()) {
			addYesNoOptions(vote, user, menu);
		} else if (String.join("X. ", vote.getVoteOptions()).length() + 3 + vote.getDescription().length() < 160) {
			addVoteOptions(vote, menu);
		}

		if (!menu.hasOptions() || menu.getMenuCharLength() < 160) {
			menu.addMenuOption(voteMenus + "respond?voteUid=" + vote.getUid(), ussdSupport.getMessage("options.back", user));
		}

		if (menu.getMenuCharLength() < 160) {
			menu.addMenuOption("start_force", ussdSupport.getMessage("options.skip", user));
		}

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional(readOnly = true)
	public Request processRespondToVote(String inputNumber, String voteUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Vote vote = voteBroker.load(voteUid);
		final USSDMenu ussdMenu = assembleVoteMenu(user, vote);
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processVoteAndWelcome(String inputNumber, String voteUid, String response) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		voteBroker.recordUserVote(user.getUid(), voteUid, response);
		final String prompt = ussdSupport.getMessage(thisSection, UssdSupport.startMenu, promptKey + ".vote-recorded", user);
		cacheManager.clearRsvpCacheForUser(user.getUid());
		return userManager.needToPromptForLanguage(user, preLanguageSessions) ? ussdSupport.menuBuilder(ussdSupport.promptLanguageMenu(user)) :
				ussdSupport.menuBuilder(new USSDMenu(prompt, ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processVoteSubject(String msisdn, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, GROUP_PERMISSION_CREATE_GROUP_VOTE);
		// if request UID is not null then by definition we are here via confirmation return
		String nextUrl = StringUtils.isEmpty(requestUid) ? voteMenus + "type" : menuUrl("confirm", requestUid) + "&field=subject";
		if (!StringUtils.isEmpty(requestUid)) {
			cacheManager.putUssdMenuForUser(msisdn, saveVoteMenu("subject", requestUid));
		}
		// ask for group will by definition return the "no group" menu, since we are in this branch
		USSDMenu menu = possibleGroups != 0 ?
				new USSDMenu(ussdSupport.getMessage(thisSection, "subject", promptKey, user), nextUrl) :
				groupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processVoteType(String msisdn, String request, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		if (StringUtils.isEmpty(requestUid)) {
			requestUid = eventRequestBroker.createNewStyleEmptyVote(user.getUid(), request);
		}
		cacheManager.putUssdMenuForUser(msisdn, saveVoteMenu("type", requestUid));
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "type", promptKey, user));
		menu.addMenuOption(menuUrl("yes_no", requestUid),
				ussdSupport.getMessage(thisSection, "type", optionsKey + "yesno", user));
		menu.addMenuOption(menuUrl("multi_option/start", requestUid),
				ussdSupport.getMessage(thisSection, "type", optionsKey + "multi", user));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processYesNoSelectGroup(String msisdn, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveVoteMenu("yes_no", requestUid));
		// if the user only has one group, that gets passed in
		final String timePrompt = ussdSupport.getMessage(thisSection, "time", promptKey + ".yesno", user);
		final USSDMenu ussdMenu = groupMenu(user, timePrompt, requestUid);
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processSelectTime(String msisdn, String requestUid, String groupUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveVoteMenu("closing", requestUid)
				+ (groupUid != null ? "&groupUid=" + groupUid : ""));
		if (groupUid != null) {
			eventRequestBroker.updateVoteGroup(user.getUid(), requestUid, groupUid);
		}
		final String prompt = ussdSupport.getMessage(thisSection, "time", promptKey, user);
		return ussdSupport.menuBuilder(timeMenu(user, prompt, requestUid));
	}

	@Override
	@Transactional
	public Request processInitiateMultiOption(String msisdn, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveVoteMenu("multi_option/start", requestUid));
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "multi", promptKey + ".start", user),
				menuUrl("multi_option/add", requestUid));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processAddVoteOption(String msisdn, String requestUid, String request, String priorInput) throws URISyntaxException {
		String userInput = StringUtils.isEmpty(priorInput) ? request : priorInput;
		User user = userManager.findByInputNumber(msisdn,
				saveVoteMenu("multi_option/add", requestUid) + "&priorInput=" + encodeParameter(userInput));
		// watch for duplication but service & core should both catch it
		int numberOptions = eventRequestBroker.load(requestUid).getVoteOptions().size();
		if (numberOptions > 1 && "0".equals(userInput.trim())) {
			final String timePrompt = ussdSupport.getMessage(thisSection, "time", promptKey + ".multi", user);
			final USSDMenu ussdMenu = groupMenu(user, timePrompt, requestUid);
			return ussdSupport.menuBuilder(ussdMenu);
		} else {
			final USSDMenu ussdMenu = addOptionAndReturn(user, requestUid, userInput);
			return ussdSupport.menuBuilder(ussdMenu);
		}
	}

	@Override
	@Transactional
	public Request processCustomVotingTime(String msisdn, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn, saveVoteMenu("time_custom", requestUid));
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "time", promptKey + "-custom", user));
		menu.setFreeText(true);
		menu.setNextURI(voteMenus + "confirm?requestUid=" + requestUid + "&field=custom");
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processConfirmVoteSend(String msisdn, String requestUid, String request, String priorInput, String field, VoteTime time, Boolean interrupted) throws URISyntaxException {
		final String userInput = StringUtils.isEmpty(priorInput) ? request : priorInput;
		User user = userManager.findByInputNumber(msisdn, saveVoteMenu("confirm", requestUid));
		String lastMenu = field == null ? "standard" : field;

		if (interrupted == null || !interrupted) {
			if ("standard".equals(lastMenu)) {
				setStandardTime(requestUid, time, user);
			} else if ("custom".equals(lastMenu)) {
				setCustomTime(requestUid, userInput, user);
			} else if ("subject".equals(lastMenu)) {
				adjustSubject(requestUid, userInput, user);
			}
		}

		EventRequest vote = eventRequestBroker.load(requestUid);
		String[] promptFields = new String[]{vote.getName(), "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat)};

		// note: for the moment, not allowing revision of options, because somewhat fiddly (will
		// add if demand arises)
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "confirm", promptKey, promptFields, user));
		menu.addMenuOption(voteMenus + "send?requestUid=" + requestUid, ussdSupport.getMessage(thisSection, "confirm", optionsKey + "yes", user));
		menu.addMenuOption(backVoteUrl("subject", requestUid), ussdSupport.getMessage(thisSection, "confirm", optionsKey + "topic", user));
		menu.addMenuOption(backVoteUrl("closing", requestUid), ussdSupport.getMessage(thisSection, "confirm", optionsKey + "time", user));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processVoteSendResetTime(String inputNumber, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		setStandardTime(requestUid, INSTANT, user);
		eventRequestBroker.finish(user.getUid(), requestUid, true);
		return ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(thisSection, "send", promptKey, user), ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processVoteSendDo(String inputNumber, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);

		try {
			String createdUid = eventRequestBroker.finish(user.getUid(), requestUid, true);
			Event vote = eventBroker.load(createdUid);
			int eventsLeft = accountFeaturesBroker.numberEventsLeftForParent(vote.getUid());
			final String prompt = eventsLeft < EVENT_LIMIT_WARNING_THRESHOLD ?
					ussdSupport.getMessage(thisSection, "send", promptKey + ".limit", String.valueOf(eventsLeft), user) :
					ussdSupport.getMessage(thisSection, "send", promptKey, user);
			USSDMenu menu = new USSDMenu(prompt, ussdSupport.optionsHomeExit(user, false));
			return ussdSupport.menuBuilder(menu);
		} catch (EventStartTimeNotInFutureException e) {
			final String messageKey = USSDSection.VOTES.toKey() + "send.err.past.";
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(messageKey + promptKey, user));
			menu.setFreeText(false);
			menu.addMenuOption(voteMenus + "send-reset" + entityUidUrlSuffix + requestUid, ussdSupport.getMessage(messageKey + "yes", user));
			menu.addMenuOption(backVoteUrl("time", requestUid), ussdSupport.getMessage(messageKey + "no", user));
			return ussdSupport.menuBuilder(menu);

		} catch (AccountLimitExceededException e) {
			return ussdSupport.menuBuilder(eventUtil.outOfEventsMenu(thisSection, voteMenus + "new", ussdSupport.optionsHomeExit(user, true), user));
		}
	}

	@Override
	@Transactional
	public Optional<USSDMenu> processPossibleMassVote(User user, Group group) {
		return voteBroker.getMassVoteOpenForGroup(group).map(vote -> processMassVoteOpening(vote, user));
	}

	@Override
	public Request processKnownMassVote(String inputNumber, String voteUid) throws URISyntaxException {
		final User user = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
		final Vote vote = voteBroker.load(voteUid);
		return ussdSupport.menuBuilder(processMassVoteOpening(vote, user));
	}

	private USSDMenu processMassVoteOpening(final Vote vote, final User user) {
		if (vote.hasAdditionalLanguagePrompts()) {
			final USSDMenu langMenu = new USSDMenu(ussdSupport.getMessage("language.prompt.short", user));
			final String voteUri = voteMenus + "mass/language?voteUid=" + vote.getUid() + "&language=";
			vote.getPromptLanguages().forEach(locale ->
					langMenu.addMenuOption(voteUri + locale.toString(),
							ussdSupport.getMessage("language." + locale.getISO3Language(), user))
			);
			return langMenu;
		} else  {
			final USSDMenu menu = massVoteMenu(vote, UserMinimalProjection.extractFromUser(user), null); // handles option setting, etc.
			log.info("Initiated a mass vote, here it is: {}", menu);
			return menu;
		}
	}

	private USSDMenu massVoteMenu(Vote vote, UserMinimalProjection user, Locale language) {
		// for now, we are enforcing that multi-language prompts can only be yes or no
		if (vote.getVoteOptions().isEmpty()) {
			return massVoteYesNoMenu(vote, user, language);
		} else {
			return massVoteOptionsMenu(vote, language);
		}
	}

	// todo : think about adding description back too
	private USSDMenu massVoteYesNoMenu(Vote vote, UserMinimalProjection user, Locale language) {
		final Locale lang = language == null ? Locale.ENGLISH : language;
		final String prompt = vote.getLanguagePrompt(language).orElse(vote.getName());
		final USSDMenu menu = new USSDMenu(prompt);
		final String urlBase = voteMenus + "mass/record?voteUid=" + vote.getUid() + addLang(language) + "&response=";
		menu.addMenuOption(urlBase + "YES", ussdSupport.getMessage("options.yes", lang.toString()));
		menu.addMenuOption(urlBase + "NO", ussdSupport.getMessage("options.no", lang.toString()));
		if (!vote.shouldExcludeAbstention()) {
			menu.addMenuOption(urlBase + "ABSTAIN", ussdSupport.getMessage("mvote.options.abstain", lang.toString()));
		}
		return menu;
	}

	private USSDMenu massVoteOptionsMenu(Vote vote, Locale language) {
		final Locale lang = language == null ? Locale.ENGLISH : language;
		final USSDMenu menu = new USSDMenu(vote.getLanguagePrompt(lang).orElse(vote.getName()));
		final String urlBase = voteMenus + "mass/record?voteUid=" + vote.getUid() + addLang(language) + "&response=";

		// watch the ordering (if the vote options are randomized ...)
		final List<String> baseOptions = vote.getVoteOptions();
		final List<String> displayOptions = vote.hasMultiLangOptions() ? vote.getOptionsForLang(language, baseOptions) : baseOptions;

		for (int i = 0; i < baseOptions.size(); i++) {
			menu.addMenuOption(urlBase + USSDUrlUtil.encodeParameter(baseOptions.get(i)), displayOptions.get(i));
		}
		return menu;
	}

	private String addLang(Locale language) {
		return (language != null ? "&language=" + language : "");
	}

	@Override
	@Transactional
	public Request processMassVoteLanguageSelection(String inputNumber, String voteUid, Locale language) throws URISyntaxException {
		// todo : store menu in case interrupted
		final UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		userManager.updateUserLanguage(user.getUid(), language, UserInterfaceType.USSD);
		final Vote vote = voteBroker.load(voteUid);
		return ussdSupport.menuBuilder(massVoteMenu(vote, user, language));
	}

	@Override
	@Transactional
	public Request processMassVoteResponse(String inputNumber, String voteUid, String response, Locale language, Integer voteCount) throws URISyntaxException {
		final Vote vote = voteBroker.load(voteUid);
		final UserMinimalProjection user = userManager.findUserMinimalByMsisdn(inputNumber);
		if (vote.hasPostVotePrompt()) {
			// todo : clear cache, etc
			voteBroker.recordUserVote(user.getUid(), voteUid, response);
			final String prompt = vote.getPostVotePrompt(language).orElse(ussdSupport.getMessage("vote.start.prompt.vote-recorded", user));
			return ussdSupport.menuBuilder(new USSDMenu(prompt));
		} else {
			final Group group = vote.getAncestorGroup();
			final int nextVotePlace = voteCount == null ? 2 : voteCount + 1;
			final Optional<Vote> nextVote = voteBroker.getNextMassVoteForGroup(group, nextVotePlace);
			if (nextVote.isPresent()) {
				return ussdSupport.menuBuilder(massVoteMenu(nextVote.get(), user, language));
			} else {
				return processVoteAndWelcome(inputNumber, voteUid, response);
			}
		}
	}


	private void setStandardTime(String requestUid, VoteTime time, User user) {
		ZonedDateTime proposedDateTime = null;
		ZonedDateTime zonedNow = Instant.now().atZone(DateTimeUtil.getSAST());

		switch (time) {
			case INSTANT:
				proposedDateTime = zonedNow.plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS);
				break;
			case HOUR:
				proposedDateTime = zonedNow.plusHours(1L);
				break;
			case DAY:
				proposedDateTime = zonedNow.plusDays(1L);
				break;
			case WEEK:
				proposedDateTime = zonedNow.plusWeeks(1L);
				break;
			case CUSTOM:
				proposedDateTime = zonedNow.plusMinutes(7L);
				break;
		}

		eventRequestBroker.updateEventDateTime(user.getUid(), requestUid,
				proposedDateTime.toLocalDateTime());
	}

	private void adjustSubject(String requestUid, String userInput, User user) {
		eventRequestBroker.updateName(user.getUid(), requestUid, userInput);
		EventRequest vote = eventRequestBroker.load(requestUid);
		if (vote.getEventStartDateTime().isBefore(Instant.now().plus(7, ChronoUnit.MINUTES))) {
			// user is manipulating an "instant" vote so need to reset the counter, else may expire before send
			eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, LocalDateTime.now().plusMinutes(7L));
		}
	}

	private void setCustomTime(String requestUid, String userInput, User user) {
		LocalDateTime parsedTime = eventUtil.parseDateTime(userInput, null);
		userLogger.recordUserInputtedDateTime(user.getUid(), userInput, "vote-custom", UserInterfaceType.USSD);
		eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, parsedTime);
	}

	private USSDMenu addOptionAndReturn(User user, String requestUid, String userInput) {
		try {
			int newNumber = eventRequestBroker.addVoteOption(user.getUid(), requestUid, userInput);
			final String prompt = newNumber > 1 ?
					ussdSupport.getMessage(thisSection, "multi", promptKey + ".more", user) :
					ussdSupport.getMessage(thisSection, "multi", promptKey + ".1more", user);
			return new USSDMenu(prompt, menuUrl("multi_option/add", requestUid));
		} catch (InvalidParameterException e) {
			final String prompt = ussdSupport.getMessage(thisSection, "multi", promptKey + ".toolong", user);
			return new USSDMenu(prompt, menuUrl("multi_option/add", requestUid));
		}
	}

	private void addYesNoOptions(Vote vote, User user, USSDMenu menu) {
		final String optionMsgKey = UssdSupport.voteKey + "." + optionsKey;
		final String voteUri = voteMenus + "record?voteUid=" + vote.getUid() + "&response=";
		menu.addMenuOption(voteUri + "YES", ussdSupport.getMessage(optionMsgKey + "yes", user));
		menu.addMenuOption(voteUri + "NO", ussdSupport.getMessage(optionMsgKey + "no", user));
		if (!vote.shouldExcludeAbstention()) {
			menu.addMenuOption(voteUri + "ABSTAIN", ussdSupport.getMessage(optionMsgKey + "abstain", user));
		}
	}

	private void addVoteOptions(Vote vote, USSDMenu menu) {
		final String voteUri = voteMenus + "record?voteUid=" + vote.getUid() + "&response=";
		vote.getVoteOptions().forEach(o -> {
			menu.addMenuOption(voteUri + USSDUrlUtil.encodeParameter(o), o);
		});
	}

	private String menuUrl(String menu, String requestUid) {
		return voteMenus + menu + "?requestUid=" + requestUid;
	}

	private USSDMenu groupMenu(User user, String timePrompt, String requestUid) throws URISyntaxException {
		int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, GROUP_PERMISSION_CREATE_GROUP_VOTE);
		if (possibleGroups == 1) {
			Group group = permissionBroker.getActiveGroupsSorted(user, GROUP_PERMISSION_CREATE_GROUP_VOTE).get(0);
			eventRequestBroker.updateVoteGroup(user.getUid(), requestUid, group.getUid());
			return timeMenu(user, timePrompt, requestUid);
		} else {
			final USSDGroupUtil.GroupMenuBuilder groupMenuBuilder = new USSDGroupUtil.GroupMenuBuilder(user, thisSection)
					.messageKey("group")
					.urlForExistingGroup("closing?requestUid=" + requestUid);
			return groupUtil.askForGroup(groupMenuBuilder);
		}
	}

	private USSDMenu timeMenu(User user, String prompt, String requestUid) {
		USSDMenu menu = new USSDMenu(prompt);

		String nextUrl = voteMenus + "confirm?requestUid=" + requestUid + "&field=standard&time=";
		String optionKey = UssdSupport.voteKey + ".time." + optionsKey;

		menu.addMenuOption(nextUrl + INSTANT.name(), ussdSupport.getMessage(optionKey + "instant", user));
		menu.addMenuOption(nextUrl + HOUR.name(), ussdSupport.getMessage(optionKey + "hour", user));
		menu.addMenuOption(nextUrl + DAY.name(), ussdSupport.getMessage(optionKey + "day", user));
		menu.addMenuOption(nextUrl + WEEK.name(), ussdSupport.getMessage(optionKey + "week", user));
		menu.addMenuOption(voteMenus + "time_custom?requestUid=" + requestUid, ussdSupport.getMessage(optionKey + "custom", user));
		return menu;
	}


}
