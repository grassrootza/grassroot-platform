package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.GeographicSearchType;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller for the USSD menu more option
 */
@Slf4j
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDAdvancedHomeController extends USSDBaseController {

    private static final String ROOT_PATH = homePath + moreMenus;

    private static final int PAGE_SIZE = 2;
    private static final Integer searchRadius = 5000;
    private static final USSDSection thisSection = USSDSection.HOME;

    private final UssdLocationServicesBroker ussdLocationServicesBroker;
    private final UserLocationLogRepository userLocationLogRepository; // not great, but avoiding some nasty async issues

    private final ObjectLocationBroker objectLocationBroker;
    private final EventBroker eventBroker;

    @Autowired
    public USSDAdvancedHomeController(UssdLocationServicesBroker ussdLocationServicesBroker,
                                      UserLocationLogRepository userLocationLogRepository,
                                      ObjectLocationBroker objectLocationBroker,
                                      EventBroker eventBroker){
        this.ussdLocationServicesBroker = ussdLocationServicesBroker;
        this.userLocationLogRepository = userLocationLogRepository;
        this.objectLocationBroker = objectLocationBroker;
        this.eventBroker = eventBroker;
    }

    @RequestMapping(value = ROOT_PATH + startMenu)
    @ResponseBody
    public Request moreOptions(@RequestParam String msisdn) throws URISyntaxException{
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "more", promptKey, user));
        ussdMenu.addMenuOption(safetyMenus + startMenu, getMessage(thisSection, "more", optionsKey + "safety", user));
        ussdMenu.addMenuOption(moreMenus + "/public/mtgs", getMessage(thisSection, "more", optionsKey + "publicmtgs", user));
        ussdMenu.addMenuOption(startMenu, getMessage(optionsKey + "back", user));
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = ROOT_PATH + "/public/mtgs")
    @ResponseBody
    public Request getPublicMeetingsNearUser(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) boolean repeat) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        GeoLocation guessedLocation = objectLocationBroker.fetchBestGuessUserLocation(user.getUid());

        USSDMenu ussdMenu;

        if (guessedLocation != null) {
            List<ObjectLocation> listOfPublicMeetingsNearUser = objectLocationBroker
                    .fetchMeetingLocationsNearUser(user, guessedLocation, searchRadius, GeographicSearchType.PUBLIC, null);
            log.info("Size of meetings array in home more controller= {}",listOfPublicMeetingsNearUser.size());
            ussdMenu = listOfPublicMeetingsNearUser.isEmpty() ?
                    haveLocationButNoMeetings(user, repeat) :
                    haveLocationAndMeetings(user, repeat, listOfPublicMeetingsNearUser, page == null ? 0 : page);
        } else {
            ussdMenu = haveNoLocation(user, repeat);
        }
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = ROOT_PATH + "/public/mtgs/details")
    @ResponseBody
    public Request meetingDetails(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam String meetingUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        Meeting meeting = (Meeting) eventBroker.load(meetingUid);

        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "public", promptKey + ".details", new String[] {
                meeting.getName(), dateTimeFormat.format(meeting.getEventDateTimeAtSAST()), meeting.getEventLocation()
        }, user));

        ussdMenu.addMenuOption(moreMenus + "public/mtgs", getMessage(optionsKey + "back", user));
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = homePath + moreMenus + startMenu + "/track-me")
    @ResponseBody
    public Request trackMe(@RequestParam(value = phoneNumber) String inputNumber)throws URISyntaxException{
        // todo : cache for interruption
        User user = userManager.findByInputNumber(inputNumber);
        boolean tracking = ussdLocationServicesBroker.addUssdLocationLookupAllowed(user.getUid(), UserInterfaceType.USSD);
        USSDMenu menu;
        if (tracking) {
            lookupAndStoreLocation(user.getUid());
            menu = new USSDMenu(getMessage(thisSection, "public", "track.prompt.okay", user));
            menu.addMenuOption(moreMenus + "/public/mtgs?repeat=true", getMessage(thisSection, "public", "track.options.again", user));
            addBackOption(menu, user);
        } else {
            menu = new USSDMenu(getMessage(thisSection, "public", "track.prompt.denied", user));
            addBackOption(menu, user);
            menu.addMenuOption(startMenu, getMessage(optionsKey + "back.main", user));
            menu.addMenuOption("exit", getMessage("exit.option", user));
        }
        return menuBuilder(menu);
    }

    private USSDMenu haveLocationAndMeetings(User user, boolean repeat, List<ObjectLocation> publicMeetings, int pageNumber) {
        final USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "public",
                promptKey + ".list" + (repeat ? ".repeat" : ""), String.valueOf(publicMeetings.size()), user));
        publicMeetings.stream()
                .skip(pageNumber * PAGE_SIZE)
                .limit(PAGE_SIZE)
                .forEach(pm -> ussdMenu.addMenuOption(moreMenus + "/public/mtgs/details?meetingUid=" + pm.getUid(),
                    assembleDescription(pm)));
        if (publicMeetings.size() > (pageNumber + 1) * PAGE_SIZE) {
            ussdMenu.addMenuOption(moreMenus + "public/mtgs?page=" + (pageNumber + 1), getMessage(optionsKey + "more", user));
        }
        if (pageNumber == 0) {
            addBackOption(ussdMenu, user);
        } else {
            ussdMenu.addMenuOption(moreMenus + "public/mtgs?page=" + (pageNumber - 1), getMessage(optionsKey + "back", user));
        }
        return ussdMenu;
    }

    private USSDMenu haveLocationButNoMeetings(User user, boolean repeat) {
        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "public",
                promptKey + ".none" + (repeat ? ".repeat" : ""), user));
        if (!repeat) {
            addTryTrackMeOptions(ussdMenu, user);
        } else {
            addBackOption(ussdMenu, user);
        }
        return ussdMenu;
    }

    private USSDMenu haveNoLocation(User user, boolean repeat) {
        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "public",
                promptKey + ".nolocation" + (repeat ? ".repeat" : ""), user));
        if (!repeat) {
            addTryTrackMeOptions(ussdMenu, user);
        } else {
            addBackOption(ussdMenu,user);
        }
        return ussdMenu;
    }

    private void addTryTrackMeOptions(USSDMenu menu, User user) {
        menu.addMenuOption(moreMenus + startMenu + "/track-me",
                getMessage(thisSection, "public", optionsKey + "track", user));
        addBackOption(menu, user);
    }

    private void addBackOption(USSDMenu ussdMenu,User user) {
        ussdMenu.addMenuOption(moreMenus + startMenu, getMessage(optionsKey + "back", user));
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
