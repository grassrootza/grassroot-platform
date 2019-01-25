package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDMenuUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static za.org.grassroot.webapp.controller.ussd.UssdSupport.*;

@Service
public class UssdAdvancedHomeServiceImpl implements UssdAdvancedHomeService {
	private final Logger log = LoggerFactory.getLogger(UssdAdvancedHomeServiceImpl.class);

	private static final int PAGE_SIZE = 2;
	private static final Integer searchRadius = 5000;
	private static final USSDSection thisSection = USSDSection.HOME;

	private final UssdLocationServicesBroker ussdLocationServicesBroker;
	private final UserLocationLogRepository userLocationLogRepository; // not great, but avoiding some nasty async issues
	private final GeoLocationBroker geoLocationBroker;
	private final EventBroker eventBroker;
	private final UserManagementService userManager;
	private final UssdSupport ussdSupport;

	public UssdAdvancedHomeServiceImpl(UssdLocationServicesBroker ussdLocationServicesBroker, UserLocationLogRepository userLocationLogRepository, GeoLocationBroker geoLocationBroker, EventBroker eventBroker, UserManagementService userManager, UssdSupport ussdSupport) {
		this.ussdLocationServicesBroker = ussdLocationServicesBroker;
		this.userLocationLogRepository = userLocationLogRepository;
		this.geoLocationBroker = geoLocationBroker;
		this.eventBroker = eventBroker;
		this.userManager = userManager;
		this.ussdSupport = ussdSupport;
	}

	@Override
	@Transactional
	public Request processMoreOptions(String msisdn) throws URISyntaxException {
		User user = userManager.findByInputNumber(msisdn);
		USSDMenu ussdMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "more", promptKey, user));
		ussdMenu.addMenuOption(safetyMenus + startMenu, ussdSupport.getMessage(thisSection, "more", optionsKey + "safety", user));
		ussdMenu.addMenuOption(moreMenus + "public/mtgs", ussdSupport.getMessage(thisSection, "more", optionsKey + "publicmtgs", user));
		ussdMenu.addMenuOption(startMenu, ussdSupport.getMessage(optionsKey + "back", user));
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processGetPublicMeetingNearUser(String inputNumber, Integer page, boolean repeat) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		GeoLocation guessedLocation = geoLocationBroker.fetchBestGuessUserLocation(user.getUid());

		USSDMenu ussdMenu;

		if (guessedLocation != null) {
			List<ObjectLocation> listOfPublicMeetingsNearUser = geoLocationBroker
					.fetchMeetingLocationsNearUser(user, guessedLocation, searchRadius, GeographicSearchType.PUBLIC, null);
			log.info("Size of meetings array in home more controller= {}", listOfPublicMeetingsNearUser.size());
			ussdMenu = listOfPublicMeetingsNearUser.isEmpty() ?
					haveLocationButNoMeetings(user, repeat) :
					haveLocationAndMeetings(user, repeat, listOfPublicMeetingsNearUser, page == null ? 0 : page);
		} else {
			ussdMenu = haveNoLocation(user, repeat);
		}
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processMeetingDetails(String inputNumber, String meetingUid) throws URISyntaxException {
		User user = userManager.findByInputNumber(inputNumber);
		Meeting meeting = (Meeting) eventBroker.load(meetingUid);

		USSDMenu ussdMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "public", promptKey + ".details", new String[]{
				meeting.getName(), dateTimeFormat.format(meeting.getEventDateTimeAtSAST()), meeting.getEventLocation()
		}, user));

		ussdMenu.addMenuOption(moreMenus + "public/mtgs", ussdSupport.getMessage(optionsKey + "back", user));
		return ussdSupport.menuBuilder(ussdMenu);
	}

	@Override
	@Transactional
	public Request processTrackMe(String inputNumber) throws URISyntaxException {
		// todo : cache for interruption
		User user = userManager.findByInputNumber(inputNumber);
		boolean tracking = ussdLocationServicesBroker.addUssdLocationLookupAllowed(user.getUid(), UserInterfaceType.USSD);
		USSDMenu menu;
		if (tracking) {
			lookupAndStoreLocation(user.getUid());
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "public", "track.prompt.okay", user));
			menu.addMenuOption(moreMenus + "public/mtgs?repeat=true", ussdSupport.getMessage(thisSection, "public", "track.options.again", user));
			addBackOption(menu, user);
		} else {
			menu = new USSDMenu(ussdSupport.getMessage(thisSection, "public", "track.prompt.denied", user));
			addBackOption(menu, user);
			menu.addMenuOption(startMenu, ussdSupport.getMessage(optionsKey + "back.main", user));
			menu.addMenuOption("exit", ussdSupport.getMessage("exit.option", user));
		}
		return ussdSupport.menuBuilder(menu);
	}

	private USSDMenu haveLocationAndMeetings(User user, boolean repeat, List<ObjectLocation> publicMeetings, int pageNumber) {
		final USSDMenu ussdMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "public",
				promptKey + ".list" + (repeat ? ".repeat" : ""), String.valueOf(publicMeetings.size()), user));
		publicMeetings.stream()
				.skip(pageNumber * PAGE_SIZE)
				.limit(PAGE_SIZE)
				.forEach(pm -> ussdMenu.addMenuOption(moreMenus + "public/mtgs/details?meetingUid=" + pm.getUid(),
						assembleDescription(pm)));
		if (publicMeetings.size() > (pageNumber + 1) * PAGE_SIZE) {
			ussdMenu.addMenuOption(moreMenus + "public/mtgs?page=" + (pageNumber + 1), ussdSupport.getMessage(optionsKey + "more", user));
		}
		if (pageNumber == 0) {
			addBackOption(ussdMenu, user);
		} else {
			ussdMenu.addMenuOption(moreMenus + "public/mtgs?page=" + (pageNumber - 1), ussdSupport.getMessage(optionsKey + "back", user));
		}
		return ussdMenu;
	}

	private USSDMenu haveLocationButNoMeetings(User user, boolean repeat) {
		USSDMenu ussdMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "public",
				promptKey + ".none" + (repeat ? ".repeat" : ""), user));
		if (!repeat) {
			addTryTrackMeOptions(ussdMenu, user);
		} else {
			addBackOption(ussdMenu, user);
		}
		return ussdMenu;
	}

	private USSDMenu haveNoLocation(User user, boolean repeat) {
		USSDMenu ussdMenu = new USSDMenu(ussdSupport.getMessage(thisSection, "public",
				promptKey + ".nolocation" + (repeat ? ".repeat" : ""), user));
		if (!repeat) {
			addTryTrackMeOptions(ussdMenu, user);
		} else {
			addBackOption(ussdMenu, user);
		}
		return ussdMenu;
	}

	private void addTryTrackMeOptions(USSDMenu menu, User user) {
		menu.addMenuOption(moreMenus + startMenu + "/track-me",
				ussdSupport.getMessage(thisSection, "public", optionsKey + "track", user));
		addBackOption(menu, user);
	}

	private void addBackOption(USSDMenu ussdMenu, User user) {
		ussdMenu.addMenuOption(moreMenus + startMenu, ussdSupport.getMessage(optionsKey + "back", user));
	}

	private void lookupAndStoreLocation(String userUid) {
		CompletableFuture<GeoLocation> locationFetch = ussdLocationServicesBroker.getUssdLocation(userUid);
		if (locationFetch != null) {
			locationFetch.thenAccept(gl -> {
				userLocationLogRepository.save(new UserLocationLog(Instant.now(), userUid, gl, LocationSource.LOGGED_APPROX));
			});
		}
	}

	private String assembleDescription(ObjectLocation objectLocation) {
		return objectLocation.getName() + ", at " + objectLocation.getLocationDescription();
	}

}
