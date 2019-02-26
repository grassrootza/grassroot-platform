package za.org.grassroot.webapp.controller.ussd;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingRequest;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.dto.task.TaskMinimalDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.exception.SeloParseDateTimeFailure;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.EventRequestBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.task.enums.EventListTimeType;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static za.org.grassroot.core.util.DateTimeUtil.*;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.entityUidUrlSuffix;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.groupUidUrlSuffix;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.previousMenu;
import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;
import static za.org.grassroot.webapp.enums.USSDSection.fromString;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

@Service
public class UssdMeetingServiceImpl implements UssdMeetingService {
	private final Logger log = LoggerFactory.getLogger(UssdMeetingServiceImpl.class);

	private static final int EVENT_LIMIT_WARNING_THRESHOLD = 5; // only warn when below this

	private boolean eventMonthlyLimitActive;
	private boolean locationRequestEnabled;

	private final TaskBroker taskBroker;
	private final UssdSupport ussdSupport;
	private final UserManagementService userManager;
	private final EventBroker eventBroker;
	private final EventRequestBroker eventRequestBroker;
	private final EventLogBroker eventLogBroker;
	private final AccountFeaturesBroker accountFeaturesBroker;
	private final GeoLocationBroker geoLocationBroker;
	private final USSDEventUtil eventUtil;
	private final USSDGroupUtil groupUtil;
	private final GroupBroker groupBroker;
	private final CacheUtilService cacheManager;

	private static final List<String> menuSequence =
			Arrays.asList(startMenu, subjectMenu, placeMenu, timeMenu, confirmMenu, send);

	public UssdMeetingServiceImpl(@Value("${grassroot.events.limit.enabled:false}") boolean eventMonthlyLimitActive,
								  @Value("${grassroot.ussd.location.enabled:false}") boolean locationRequestEnabled,
								  TaskBroker taskBroker, UssdSupport ussdSupport, UserManagementService userManager, EventBroker eventBroker, EventRequestBroker eventRequestBroker, EventLogBroker eventLogBroker, AccountFeaturesBroker accountFeaturesBroker, GeoLocationBroker geoLocationBroker, USSDEventUtil eventUtil, USSDGroupUtil groupUtil, GroupBroker groupBroker, CacheUtilService cacheManager) {
		this.eventMonthlyLimitActive = eventMonthlyLimitActive;
		this.locationRequestEnabled = locationRequestEnabled;
		this.ussdSupport = ussdSupport;
		this.userManager = userManager;
		this.eventBroker = eventBroker;
		this.eventRequestBroker = eventRequestBroker;
		this.eventLogBroker = eventLogBroker;
		this.accountFeaturesBroker = accountFeaturesBroker;
		this.geoLocationBroker = geoLocationBroker;
		this.eventUtil = eventUtil;
		this.groupUtil = groupUtil;
		this.groupBroker = groupBroker;
		this.cacheManager = cacheManager;
		this.taskBroker = taskBroker;
	}

	@Override
	public USSDMenu assembleRsvpMenu(User user, Event meeting) {
		// do this so various bits of assembly are guaranteed to happen in a TX
		TaskMinimalDTO mtgDetails = taskBroker.fetchDescription(user.getUid(), meeting.getUid(), TaskType.MEETING);
		String[] meetingDetails = new String[]{mtgDetails.getAncestorGroupName(),
				mtgDetails.getCreatedByUserName(), mtgDetails.getTitle(),
				meeting.getEventDateTimeAtSAST().format(UssdSupport.dateTimeFormat)};

		// if the composed message is longer than 120 characters, we are going to go over, so return a shortened message
		String defaultPrompt = ussdSupport.getMessage(USSDSection.HOME, UssdSupport.startMenu, UssdSupport.promptKey + "-rsvp", meetingDetails, user);
		if (defaultPrompt.length() > 120) {
			defaultPrompt = ussdSupport.getMessage(USSDSection.HOME, UssdSupport.startMenu, UssdSupport.promptKey + "-rsvp.short", meetingDetails, user);
		}

		String optionUri = meetingMenus + "rsvp" + entityUidUrlSuffix + meeting.getUid();
		USSDMenu openingMenu = new USSDMenu(defaultPrompt);
		openingMenu.setMenuOptions(new LinkedHashMap<>(ussdSupport.optionsYesNo(user, optionUri, optionUri)));

		if (!StringUtils.isEmpty(meeting.getDescription())) {
			openingMenu.addMenuOption(meetingMenus + "description?mtgUid=" + meeting.getUid() + "&back=respond", ussdSupport.getMessage("home.generic.moreinfo", user));
		} else {
			openingMenu.addMenuOption(optionUri + "&confirmed=maybe", ussdSupport.getMessage("home.start.rsvp.options.maybe", user));
		}

		return openingMenu;
	}

	@Override
	@Transactional(readOnly = true)
	public Request processRespondToMtg(String inputNumber, String mtgUid) throws URISyntaxException {
		final User user = userManager.findByInputNumber(inputNumber);
		final Meeting mtg = eventBroker.loadMeeting(mtgUid);
		return ussdSupport.menuBuilder(assembleRsvpMenu(user, mtg));
	}

	@Override
	@Transactional(readOnly = true)
	public Request processShowMeetingDescription(String inputNumber, String mtgUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Meeting meeting = eventBroker.loadMeeting(mtgUid);

		USSDMenu menu = new USSDMenu(meeting.getDescription(),
				ussdSupport.optionsYesNo(user, meetingMenus + "rsvp" + entityUidUrlSuffix + meeting.getUid()));

		menu.addMenuOption(meetingMenus + "respond?mtgUid=" + meeting.getUid(), ussdSupport.getMessage("options.back", user));

		if (menu.getMenuCharLength() < 160) {
			menu.addMenuOption("start_force", ussdSupport.getMessage("options.skip", user));
		}

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processRsvpAndWelcome(String inputNumber, String meetingUid, String attending) throws URISyntaxException {
		String welcomeKey;
		User user = userManager.loadOrCreateUser(inputNumber, UserInterfaceType.USSD);
		Meeting meeting = eventBroker.loadMeeting(meetingUid);

		if ("yes".equals(attending)) {
			eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.YES);
			welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-yes"));
		} else if ("no".equals(attending)) {
			eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.NO);
			welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-no"));
		} else {
			eventLogBroker.rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.MAYBE);
			welcomeKey = String.join(".", Arrays.asList(homeKey, startMenu, promptKey, "rsvp-maybe"));
		}

		ussdSupport.recordExperimentResult(user.getUid(), attending);
		// now, check if user has had a few sessions and no language. if so, prompt for language.
		return userManager.needToPromptForLanguage(user, ussdSupport.preLanguageSessions) ? ussdSupport.menuBuilder(ussdSupport.promptLanguageMenu(user)) :
				ussdSupport.menuBuilder(new USSDMenu(ussdSupport.getMessage(welcomeKey, user), ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processEventPaginationHelper(String inputNumber, String section, String prompt, String menuForNew, String optionForNew, Integer pageNumber, String nextUrl, Integer pastPresentBoth, boolean includeGroupName) throws URISyntaxException {
		EventListTimeType timeType = pastPresentBoth == 1 ? EventListTimeType.FUTURE : EventListTimeType.PAST;
		return ussdSupport.menuBuilder(eventUtil.listPaginatedEvents(
				userManager.findByInputNumber(inputNumber), fromString(section),
				prompt, nextUrl, (menuForNew != null), menuForNew, optionForNew, includeGroupName, timeType, pageNumber));
	}

	@Override
	@Transactional
	public Request processMeetingOrg(String inputNumber, boolean newMeeting) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);

		USSDMenu returnMenu;
		if (newMeeting || !eventBroker.userHasEventsToView(user, EventType.MEETING, EventListTimeType.FUTURE)) {
			returnMenu = groupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection)
					.urlForExistingGroup(subjectMenu)
					.urlForCreateNewGroupPrompt(newGroupMenu)
					.urlToCreateNewGroup(groupName));
		} else {
			String prompt = ussdSupport.getMessage(thisSection, startMenu, promptKey + ".new-old", user);
			String newOption = ussdSupport.getMessage(thisSection, startMenu, optionsKey + "new", user);
			returnMenu = eventUtil.listUpcomingEvents(user, thisSection, prompt, manageMeetingMenu, true, startMenu + "?newMtg=1", newOption);
		}
		return ussdSupport.menuBuilder(returnMenu);
	}

	@Override
	@Transactional
	public Request processNewGroup(String inputNumber) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, meetingMenus + newGroupMenu);
		return ussdSupport.menuBuilder(groupUtil.createGroupPrompt(user, thisSection, groupName));
	}

	@Override
	@Transactional
	public Request processCreateGroupSetName(String inputNumber, String userResponse) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		if (!USSDGroupUtil.isValidGroupName(userResponse)) {
			return ussdSupport.menuBuilder(groupUtil.invalidGroupNamePrompt(user, userResponse, thisSection, groupName));
		} else {
			MembershipInfo membershipInfo = new MembershipInfo(user.getPhoneNumber(), GroupRole.ROLE_GROUP_ORGANIZER, user.getDisplayName());
			Group group = groupBroker.create(user.getUid(), userResponse, null, Collections.singleton(membershipInfo), GroupPermissionTemplate.DEFAULT_GROUP, null, null, true, false, false);
			return ussdSupport.menuBuilder(groupUtil.addNumbersToGroupPrompt(user, group, thisSection, groupHandlingMenu));
		}
	}

	@Override
	@Transactional
	public Request processCreateGroup(String inputNumber, String groupUid, String userResponse, String priorInput) throws URISyntaxException {
		USSDMenu thisMenu;

		// if priorInput exists, we have been interrupted, so use that as userInput, else use what is passed as 'request'
		String userInput = (priorInput != null) ? decodeParameter(priorInput) : userResponse;
		String includeGroup = (groupUid != null && !groupUid.equals("")) ? groupUidUrlSuffix + groupUid : ""; // there is no case where eventId is not null but groupId is

		String urlToSave = saveMenuUrlWithInput(thisSection, groupHandlingMenu, includeGroup, userInput);
		log.info("In group handling menu, have saved this URL: " + urlToSave);
		User user = userManager.findByInputNumber(inputNumber, urlToSave);

		if (!userInput.trim().equals("0")) {
			thisMenu = groupUtil.addNumbersToExistingGroup(user, groupUid, USSDSection.MEETINGS, userInput, groupHandlingMenu);
		} else {
			thisMenu = new USSDMenu(true);
			if (groupUid == null) {
				thisMenu.setPromptMessage(ussdSupport.getMessage(thisSection, groupHandlingMenu, promptKey + ".no-group", user));
				thisMenu.setNextURI(meetingMenus + groupHandlingMenu);
			} else {
				MeetingRequest meetingRequest = eventRequestBroker.createEmptyMeetingRequest(user.getUid(), groupUid);
				String mtgRequestUid = meetingRequest.getUid();
				thisMenu.setPromptMessage(ussdSupport.getMessage(thisSection, nextMenu(startMenu), promptKey, user));
				thisMenu.setNextURI(meetingMenus + nextMenu(nextMenu(startMenu)) + entityUidUrlSuffix + mtgRequestUid
						+ "&" + previousMenu + "=" + nextMenu(startMenu));
			}
		}

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processGetSubject(String inputNumber, String passedRequestUid, String groupUid, boolean interrupted, boolean revising) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber);
		log.info("event request uid: {}", passedRequestUid);
		String mtgGroupUid = groupUid != null ? groupUid :
				((MeetingRequest) eventRequestBroker.load(passedRequestUid)).getParent().getUid();
		int eventsLeft = eventMonthlyLimitActive ?
				accountFeaturesBroker.numberEventsLeftForGroup(mtgGroupUid) : 99;

		String mtgRequestUid;

		if (eventMonthlyLimitActive && eventsLeft == 0) {
			return ussdSupport.menuBuilder(outOfEventsMenu(sessionUser));
		} else {
			if (!interrupted && !revising) {
				MeetingRequest meetingRequest = eventRequestBroker.createEmptyMeetingRequest(sessionUser.getUid(), groupUid);
				mtgRequestUid = meetingRequest.getUid();
			} else {
				// i.e., reuse the one that we are passed
				mtgRequestUid = passedRequestUid;
			}

			cacheManager.putUssdMenuForUser(inputNumber, saveMeetingMenu(subjectMenu, mtgRequestUid, revising));
			String promptMessage = getSubjectPromptWithLimit(sessionUser, eventsLeft, revising);
			String nextUrl = (!revising) ? nextUrl(subjectMenu, mtgRequestUid) : confirmUrl(subjectMenu, mtgRequestUid);
			return ussdSupport.menuBuilder(new USSDMenu(promptMessage, nextUrl));
		}
	}

	@Override
	@Transactional
	public Request processGetPlace(String inputNumber, String mtgRequestUid, String priorMenu, String userInput, boolean interrupted, boolean revising) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(placeMenu, mtgRequestUid, revising));
		if (!interrupted) {
			eventUtil.updateEventRequest(user.getUid(), mtgRequestUid, priorMenu, userInput);
		}

		MeetingRequest eventRequest = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);
		log.info("okay looking up most frequent location ...");
		final String mostFreq = eventBroker.getMostFrequentLocation(eventRequest.getParent().getUid());
		log.info("and most frequent location is ... {}", mostFreq);

		String promptMessage = StringUtils.isEmpty(mostFreq) ?
				ussdSupport.getMessage(thisSection, placeMenu, promptKey, user) :
				ussdSupport.getMessage(thisSection, placeMenu, promptKey + ".freq", mostFreq, user);
		String nextUrl = (!revising) ? nextUrl(placeMenu, mtgRequestUid) : confirmUrl(placeMenu, mtgRequestUid);
		return ussdSupport.menuBuilder(new USSDMenu(promptMessage, nextUrl));
	}

	@Override
	@Transactional
	public Request processGetTime(String inputNumber, String mtgRequestUid, String priorMenu, String userInput, boolean interrupted) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, saveMeetingMenu(timeMenu, mtgRequestUid, false));

		MeetingRequest eventRequest = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);

		if (!interrupted) {
			eventUtil.updateEventRequest(sessionUser.getUid(), mtgRequestUid, priorMenu, userInput);
		}

		final LocalTime mostFreqTime = eventRequest != null && eventRequest.getParent() != null ?
				eventBroker.getMostFrequentEventTime(eventRequest.getParent().getUid()) : null;

		String promptMessage = mostFreqTime == null ?
				ussdSupport.getMessage(thisSection, timeMenu, promptKey, sessionUser) :
				ussdSupport.getMessage(thisSection, timeMenu, promptKey + ".freq", mostFreqTime.toString(), sessionUser);

		String nextUrl = meetingMenus + nextMenu(timeMenu) + entityUidUrlSuffix + mtgRequestUid + "&" + previousMenu + "=" + timeMenu;
		return ussdSupport.menuBuilder(new USSDMenu(promptMessage, nextUrl));
	}

	@Override
	@Transactional
	public Request processChangeTimeOnlyMtgReq(String inputNumber, String mtgRequestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(timeOnly, mtgRequestUid, false));
		MeetingRequest meetingRequest = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);
		String currentlySetTime = meetingRequest.getEventDateTimeAtSAST().format(getPreferredTimeFormat());

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "change", "time." + promptKey, currentlySetTime, user));
		menu.setFreeText(true);
		menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + timeOnly);

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processChangeDateOnlyMtgReq(String inputNumber, String mtgRequestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(dateOnly, mtgRequestUid, false));
		MeetingRequest meetingRequest = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);

		String currentlySetDate = meetingRequest.getEventDateTimeAtSAST().format(getPreferredDateFormat());

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "change", "date." + promptKey, currentlySetDate, user));
		menu.setFreeText(true);
		menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + dateOnly);

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processConfirmDetails(String inputNumber, String mtgRequestUid, String priorMenu, String userInput, boolean interrupted) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, saveMeetingMenu(confirmMenu, mtgRequestUid, false));

		if (!interrupted) {
			try {
				eventUtil.updateEventRequest(user.getUid(), mtgRequestUid, priorMenu, userInput);
			} catch (SeloParseDateTimeFailure e) {
				cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveMeetingMenu(priorMenu, mtgRequestUid, false));
				return handleDateTimeParseFailure(user, priorMenu, mtgRequestUid);
			}
		}

		MeetingRequest meeting = (MeetingRequest) eventRequestBroker.load(mtgRequestUid);
		if (meeting.getEventStartDateTime() == null) {
			log.error("Error! Got to meeting confirm with null start date, somehow");
			return handleDateTimeParseFailure(user, priorMenu, mtgRequestUid);
		}

		String dateString = meeting.getEventDateTimeAtSAST().format(dateTimeFormat);
		String[] confirmFields = new String[]{dateString, meeting.getName(), meeting.getEventLocation()};

		final boolean isInFuture = meeting.getEventStartDateTime().isAfter(Instant.now());

		String confirmPrompt = isInFuture ?
				ussdSupport.getMessage(thisSection, confirmMenu, promptKey, confirmFields, user) :
				ussdSupport.getMessage(thisSection, confirmMenu, promptKey + ".err.past", dateString, user);

		// based on user feedback, give options to return to any prior screen, then back here.

		USSDMenu thisMenu = new USSDMenu(confirmPrompt);

		if (isInFuture) {
			thisMenu.addMenuOption(meetingMenus + send + entityUidUrlSuffix + mtgRequestUid,
					ussdSupport.getMessage(thisSection + "." + confirmMenu + "." + optionsKey + "yes", user));
		}

		thisMenu.addMenuOption(composeBackUri(mtgRequestUid, timeOnly), composeBackMessage(user, "time"));
		thisMenu.addMenuOption(composeBackUri(mtgRequestUid, dateOnly), composeBackMessage(user, "date"));

		if (isInFuture) {
			thisMenu.addMenuOption(composeBackUri(mtgRequestUid, placeMenu), composeBackMessage(user, placeMenu));
			thisMenu.addMenuOption(composeBackUri(mtgRequestUid, subjectMenu) + "&groupUid=" + meeting.getParent().getUid(),
					composeBackMessage(user, subjectMenu));
		}

		return ussdSupport.menuBuilder(thisMenu);
	}

	@Override
	@Transactional
	public Request processSendMessage(String inputNumber, String mtgRequestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		try {
			String eventUid = eventRequestBroker.finish(user.getUid(), mtgRequestUid, true);

			USSDMenu menu = new USSDMenu(chooseSendPrompt(eventUid, user));
			final String firstOptionKey = optionsKey + "public" + (locationRequestEnabled ? ".location" : "");
			final String firstOptionUrl = meetingMenus + "public" + entityUidUrlSuffix + eventUid +
					(locationRequestEnabled ? "&useLocation=true" : "");
			menu.addMenuOption(firstOptionUrl, ussdSupport.getMessage(thisSection, send, firstOptionKey, user));

			if (locationRequestEnabled) { // give the option of not providing a location
				menu.addMenuOption(meetingMenus + "public" + entityUidUrlSuffix + eventUid + "&useLocation=false",
						ussdSupport.getMessage(thisSection, send, optionsKey + "public.nolocation", user));
			}

			menu.addMenuOption(meetingMenus + "private" + entityUidUrlSuffix + eventUid,
					ussdSupport.getMessage(thisSection, send, optionsKey + "private", user));

			if (!locationRequestEnabled) {
				menu.addMenuOptions(ussdSupport.optionsHomeExit(user, true));
			}

			return ussdSupport.menuBuilder(menu);
		} catch (EventStartTimeNotInFutureException e) {
			return handleDateTimeNotInFuture(user, mtgRequestUid);
		} catch (AccountLimitExceededException e) {
			log.error("Out of events on finishing menu, should not have reached this far except in rare circumstance...");
			return ussdSupport.menuBuilder(outOfEventsMenu(user));
		}
	}

	@Override
	@Transactional
	public Request processMakeMtgPublic(String inputNumber, String mtgUid, Boolean useLocation) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		try {
			// since getting USSD location is necessarily async, pass null location now, but update through user manager
			eventBroker.updateMeetingPublicStatus(user.getUid(), mtgUid, true, null, UserInterfaceType.USSD);
			if (useLocation != null && useLocation) {
				geoLocationBroker.logUserUssdPermission(user.getUid(), mtgUid, JpaEntityType.MEETING, false);
			}
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "public", promptKey + ".done", user));
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
			return ussdSupport.menuBuilder(menu);
		} catch (AccessDeniedException e) {
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "public", promptKey + ".access", user));
			menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processMakeMtgPrivate(String inputNumber, String mtgUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		String menuPrompt;
		try {
			eventBroker.updateMeetingPublicStatus(user.getUid(), mtgUid, false, null, UserInterfaceType.USSD);
			menuPrompt = ussdSupport.getMessage(thisSection, "private", promptKey + ".done", user);
		} catch (AccessDeniedException e) {
			menuPrompt = ussdSupport.getMessage(thisSection, "public", promptKey + ".access", user);
		}
		return ussdSupport.menuBuilder(new USSDMenu(menuPrompt, ussdSupport.optionsHomeExit(user, false)));
	}

	@Override
	@Transactional
	public Request processMeetingManage(String inputNumber, String eventUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Event event = eventBroker.load(eventUid);

		USSDMenu menu;
		if (user.equals(event.getCreatedByUser())) {
			menu = meetingCallerMenu(user, eventUid);
		} else if (event.getAllMembers().contains(user)) {
			menu = meetingAttendeeMenu(user, event);
		} else {
			throw new AccessDeniedException("Error! Should not be able to access meeting menu");
		}

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processMeetingDetails(String inputNumber, String eventUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber);
		Meeting meeting = eventBroker.loadMeeting(eventUid);

		String mtgDescription;

		if (meeting.isRsvpRequired()) {
			ResponseTotalsDTO rsvpResponses = eventLogBroker.getResponseCountForEvent(meeting);
			Group group = meeting.getAncestorGroup();
			String[] messageFields = new String[]{
					group.getName(""),
					meeting.getEventLocation(),
					convertToUserTimeZone(meeting.getEventStartDateTime(), getSAST()).format(dateTimeFormat),
					String.valueOf(meeting.getMembers().size()),
					String.valueOf(rsvpResponses.getYes()),
					String.valueOf(rsvpResponses.getNo()),
					String.valueOf(rsvpResponses.getNumberNoRSVP())};
			mtgDescription = ussdSupport.getMessage(thisSection, viewMeetingDetails, promptKey + ".rsvp", messageFields, sessionUser);
		} else {
			Group group = meeting.getAncestorGroup();
			String[] messageFields = new String[]{
					group.getName(""),
					meeting.getEventLocation(),
					convertToUserTimeZone(meeting.getEventStartDateTime(), getSAST()).format(dateTimeFormat),
					String.valueOf(meeting.getMembers().size())};
			mtgDescription = ussdSupport.getMessage(thisSection, viewMeetingDetails, promptKey, messageFields, sessionUser);
		}

		USSDMenu promptMenu = new USSDMenu(mtgDescription);
		promptMenu.addMenuOption(meetingMenus + manageMeetingMenu + entityUidUrlSuffix + eventUid,
				ussdSupport.getMessage(thisSection, viewMeetingDetails, optionsKey + "back", sessionUser)); // go back to 'manage meeting'
		promptMenu.addMenuOption(startMenu, ussdSupport.getMessage(startMenu, sessionUser)); // go to GR home menu
		promptMenu.addMenuOption("exit", ussdSupport.getMessage("exit.option", sessionUser)); // exit system
		return ussdSupport.menuBuilder(promptMenu);
	}

	@Override
	@Transactional
	public Request processChangeLocation(String inputNumber, String eventUid, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		MeetingRequest changeRequest = (requestUid == null) ? eventRequestBroker.createChangeRequest(user.getUid(), eventUid) :
				(MeetingRequest) eventRequestBroker.load(requestUid);
		cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(changeMeetingLocation, eventUid, changeRequest.getUid(), null));

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, changeMeetingLocation, promptKey, changeRequest.getEventLocation(), user));
		menu.setFreeText(true);
		menu.setNextURI(editingMtgMenuUrl(modifyConfirm, eventUid, changeRequest.getUid(), changeMeetingLocation));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processNewMeetingDate(String inputNumber, String eventUid, String requestUid) throws URISyntaxException {
		return processNewMeetingDateTime(inputNumber, eventUid, requestUid, newDate, getPreferredDateFormat(), "date.");
	}

	@Override
	@Transactional
	public Request processNewMeetingTime(String inputNumber, String eventUid, String requestUid) throws URISyntaxException {
		return processNewMeetingDateTime(inputNumber, eventUid, requestUid, newTime, getPreferredTimeFormat(), "time.");
	}

	private Request processNewMeetingDateTime(String inputNumber, String eventUid, String requestUid, String action, DateTimeFormatter preferredTimeFormat, String msgPrefix) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		MeetingRequest changeRequest = (requestUid == null) ? eventRequestBroker.createChangeRequest(user.getUid(), eventUid) :
				(MeetingRequest) eventRequestBroker.load(requestUid);
		cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(action, eventUid, changeRequest.getUid(), null));

		String existingTime = changeRequest.getEventDateTimeAtSAST().format(preferredTimeFormat);
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, "change", msgPrefix + promptKey, existingTime, user));
		menu.setFreeText(true);
		menu.setNextURI(editingMtgMenuUrl(modifyConfirm, eventUid, changeRequest.getUid(), action));
		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processModifyMeeting(String inputNumber, String eventUid, String requestUid, String action, String passedInput, String priorInput) throws URISyntaxException {
		final String userInput = (priorInput == null) ? passedInput : priorInput;

		User user = userManager.findByInputNumber(inputNumber);
		cacheManager.putUssdMenuForUser(inputNumber, editingMtgMenuUrl(modifyConfirm, eventUid, requestUid, action)
				+ "&prior_input=" + encodeParameter(userInput));

		USSDMenu menu;
		final String backUrl = meetingMenus + action + entityUidUrlSuffix + eventUid + "&" + requestUidParam + "=" + requestUid;

		eventUtil.updateExistingEvent(user.getUid(), requestUid, action, userInput);
		MeetingRequest changeMeetingRequest = (MeetingRequest) eventRequestBroker.load(requestUid);

		boolean isInFuture = changeMeetingRequest.getEventStartDateTime().isAfter(Instant.now());
		final String dateString = changeMeetingRequest.getEventDateTimeAtSAST().format(dateTimeFormat);

		final String[] updatedFields = new String[]{changeMeetingRequest.getEventLocation(), dateString};
		final String menuPrompt = isInFuture ?
				ussdSupport.getMessage(thisSection, modifyConfirm, "confirm." + promptKey, updatedFields, user) :
				ussdSupport.getMessage(thisSection, modifyConfirm, "confirm." + promptKey + ".err.past", dateString, user);

		menu = new USSDMenu(menuPrompt);

		if (isInFuture) {
			menu.addMenuOption(editingMtgMenuUrl(modifyConfirm + doSuffix, eventUid, requestUid, null),
					ussdSupport.getMessage(optionsKey + "yes", user));
		}

		menu.addMenuOption(backUrl, ussdSupport.getMessage(thisSection, modifyConfirm, optionsKey + "back", user));

		List<String> modifyOptions = new ArrayList<>(Arrays.asList(newTime, newDate, changeMeetingLocation));

		modifyOptions.remove(action);
		if (!isInFuture) {
			modifyOptions.remove(changeMeetingLocation);
		}

		for (String otherMenu : modifyOptions)
			menu.addMenuOption(editingMtgMenuUrl(otherMenu, eventUid, requestUid, null), ussdSupport.getMessage(thisSection, modifyConfirm, optionsKey + otherMenu, user));

		return ussdSupport.menuBuilder(menu);
	}

	@Override
	@Transactional
	public Request processModifyMeetingSend(String inputNumber, String eventUid, String requestUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber, null);
		try {
			String menuPrompt = ussdSupport.getMessage(thisSection, modifyConfirm, promptKey + ".done", user);
			eventRequestBroker.finishEdit(user.getUid(), eventUid, requestUid);
			return ussdSupport.menuBuilder(new USSDMenu(menuPrompt, ussdSupport.optionsHomeExit(user, false)));
		} catch (EventStartTimeNotInFutureException e) {
			// given structure of UI above, this shouldn't happen, but just in case ...
			USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, modifyConfirm, promptKey + ".err.past", user));
			menu.addMenuOption(editingMtgMenuUrl(newTime, eventUid, requestUid, null), ussdSupport.getMessage(thisSection, modifyConfirm, optionsKey + newTime, user));
			menu.addMenuOption(editingMtgMenuUrl(newDate, eventUid, requestUid, null), ussdSupport.getMessage(thisSection, modifyConfirm, optionsKey + newDate, user));
			return ussdSupport.menuBuilder(menu);
		}
	}

	@Override
	@Transactional
	public Request processMeetingCancel(String inputNumber, String eventUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber); // not saving URL on this one
		USSDMenu promptMenu = new USSDMenu(ussdSupport.getMessage(thisSection, cancelMeeting, promptKey, sessionUser));
		promptMenu.addMenuOption(meetingMenus + cancelMeeting + doSuffix + entityUidUrlSuffix + eventUid,
				ussdSupport.getMessage(optionsKey + "yes", sessionUser));
		promptMenu.addMenuOption(meetingMenus + manageMeetingMenu + entityUidUrlSuffix + eventUid,
				ussdSupport.getMessage(optionsKey + "no", sessionUser));

		return ussdSupport.menuBuilder(promptMenu);
	}

	@Override
	@Transactional
	public Request processMeetingCancelConfirmed(String inputNumber, String eventUid) throws URISyntaxException {
		User sessionUser = userManager.findByInputNumber(inputNumber, null);
		String menuPrompt = ussdSupport.getMessage(thisSection, modifyConfirm, "cancel.done", sessionUser);
		eventBroker.cancel(sessionUser.getUid(), eventUid, true);
		return ussdSupport.menuBuilder(new USSDMenu(menuPrompt, ussdSupport.optionsHomeExit(sessionUser, false)));
	}

	@Override
	@Transactional
	public Request processChangeAttendeeResponseDo(String inputNumber, String eventUid, String response) throws URISyntaxException {
		// todo: should probably just make sure that this user is in fact part of the meeting (also might not want to default to "no")

		final User user = userManager.findByInputNumber(inputNumber);
		final Event event = eventBroker.load(eventUid);
		final EventRSVPResponse userResponse = "yes".equalsIgnoreCase(response) ? EventRSVPResponse.YES : EventRSVPResponse.NO;

		eventLogBroker.rsvpForEvent(event.getUid(), user.getUid(), userResponse);

		final String key = "att_changed";
		final String suffix = entityUidUrlSuffix + eventUid;

		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, key, promptKey, user));
		menu.addMenuOption(meetingMenus + manageMeetingMenu + suffix, ussdSupport.getMessage(thisSection, key, optionsKey + "back", user));
		menu.addMenuOptions(ussdSupport.optionsHomeExit(user, false));

		return ussdSupport.menuBuilder(menu);
	}

	private USSDMenu meetingCallerMenu(User user, String eventUid) {
		final String eventSuffix = entityUidUrlSuffix + eventUid;
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, manageMeetingMenu, promptKey, user));

		// todo : add a "make public option"
		menu.addMenuOption(meetingMenus + viewMeetingDetails + eventSuffix, ussdSupport.getMessage(thisSection, viewMeetingDetails, "option", user));
		menu.addMenuOption(meetingMenus + newTime + eventSuffix, ussdSupport.getMessage(thisSection, "change_" + timeOnly, "option", user));
		menu.addMenuOption(meetingMenus + newDate + eventSuffix, ussdSupport.getMessage(thisSection, "change_" + dateOnly, "option", user));
		menu.addMenuOption(meetingMenus + changeMeetingLocation + eventSuffix, ussdSupport.getMessage(thisSection, changeMeetingLocation, "option", user));
		menu.addMenuOption(meetingMenus + cancelMeeting + eventSuffix, ussdSupport.getMessage(thisSection, cancelMeeting, "option", user));

		return menu;
	}

	private String nextMenu(String currentMenu) {
		return menuSequence.get(menuSequence.indexOf(currentMenu) + 1);
	}

	private String confirmUrl(String currentMenu, String entityUid) {
		return meetingMenus + confirmMenu + entityUidUrlSuffix + entityUid + "&" + previousMenu + "=" + currentMenu +
				"&revising=1";
	}

	private String getSubjectPromptWithLimit(User user, int eventsLeft, boolean revising) {
		if (revising || !eventMonthlyLimitActive || eventsLeft > EVENT_LIMIT_WARNING_THRESHOLD) {
			return ussdSupport.getMessage(thisSection, subjectMenu, promptKey, user);
		} else {
			return ussdSupport.getMessage(thisSection, subjectMenu, promptKey + ".limit", String.valueOf(eventsLeft), user);
		}
	}

	private USSDMenu outOfEventsMenu(User user) {
		return eventUtil.outOfEventsMenu(thisSection, meetingMenus + startMenu + "?newMtg=true",
				ussdSupport.optionsHomeExit(user, true), user);
	}

	/*
 A couple of helper methods that are quite specific to flow & structure of this controller
  */

	private String composeBackUri(String entityUid, String backMenu) {
		return meetingMenus + backMenu + entityUidUrlSuffix + entityUid + "&" + previousMenu + "=" + backMenu + "&revising=1"
				+ "&next_menu=" + confirmMenu;
	}

	/* Another helper function to compose next URL */
	public String nextUrl(String currentMenu, String entityUid) {
		return meetingMenus + nextMenu(currentMenu) + entityUidUrlSuffix + entityUid + "&" + previousMenu + "=" + currentMenu;
	}

	/* Helper method to compose URL for going to confirmation screen after revisions */

	private String composeBackMessage(User user, String backMenu) {
		return ussdSupport.getMessage(thisSection + "." + confirmMenu + "." + optionsKey + backMenu, user);
	}

	private Request handleDateTimeParseFailure(User user, String priorMenu, String mtgRequestUid) throws URISyntaxException {
		final String keyRoot = mtgKey + ".parse.error." + priorMenu;
		final String prompt = ussdSupport.getMessage(keyRoot, user);
		USSDMenu menu = new USSDMenu(prompt);
		menu.setFreeText(true);
		menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + priorMenu);
		return ussdSupport.menuBuilder(menu);
	}

	private String chooseSendPrompt(String eventUid, User user) {
		if (!eventMonthlyLimitActive) {
			return ussdSupport.getMessage(thisSection, send, promptKey, user);
		} else {
			int numberEventsLeft = accountFeaturesBroker.numberEventsLeftForParent(eventUid);
			return numberEventsLeft < EVENT_LIMIT_WARNING_THRESHOLD ?
					ussdSupport.getMessage(thisSection, send, promptKey + ".limit", String.valueOf(numberEventsLeft), user) :
					ussdSupport.getMessage(thisSection, send, promptKey, user);
		}
	}

	private Request handleDateTimeNotInFuture(User user, String mtgRequestUid) throws URISyntaxException {
		USSDMenu menu = new USSDMenu(ussdSupport.getMessage(thisSection, send, promptKey + ".err.past", user));
		menu.setFreeText(true);
		menu.setNextURI(mtgMenu(confirmMenu, mtgRequestUid) + "&" + previousMenu + "=" + timeMenu);
		return ussdSupport.menuBuilder(menu);
	}

	/*
	Method and menus for a non-creating user to view & change their attendance
	 */
	private USSDMenu meetingAttendeeMenu(User user, Event event) {

		final EventLog userResponse = eventLogBroker.findUserResponseIfExists(user.getUid(), event.getUid());

		final String attendeeKey = "attendee";
		final String suffix = entityUidUrlSuffix + event.getUid();
		final String basePath = meetingMenus + "change-response" + suffix + "&response=";
		final String[] fields = new String[]{event.getAncestorGroup().getName(""),
				event.getEventDateTimeAtSAST().format(dateTimeFormat)};

		USSDMenu menu;
		if (userResponse != null) {
			if (EventRSVPResponse.YES.equals(userResponse.getResponse())) {
				menu = new USSDMenu(ussdSupport.getMessage(thisSection, attendeeKey, "rsvpyes." + promptKey, fields, user));
				menu.addMenuOption(basePath + "no", ussdSupport.getMessage(thisSection, attendeeKey, optionsKey + "rsvpno", user));
			} else {
				menu = new USSDMenu(ussdSupport.getMessage(thisSection, attendeeKey, "rsvpno." + promptKey, fields, user));
				menu.addMenuOption(basePath + "yes", ussdSupport.getMessage(thisSection, attendeeKey, optionsKey + "rsvpyes", user));
			}
		} else {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, attendeeKey, "noreply." + promptKey, fields, user));
			menu.addMenuOption(basePath + "yes", ussdSupport.getMessage(thisSection, "attendee", optionsKey + "rsvpyes", user));
			menu.addMenuOption(basePath + "no", ussdSupport.getMessage(thisSection, "attendee", optionsKey + "rsvpno", user));
		}

		menu.addMenuOption(meetingMenus + viewMeetingDetails + suffix, ussdSupport.getMessage(thisSection, viewMeetingDetails, "option", user));

		return menu;
	}


}
