package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller for the USSD menu more option
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDAdvancedHomeController extends USSDController {

    private static final Logger logger = LoggerFactory.getLogger(USSDMeetingController.class);
    private static final  String keyStart = "start";
    private static final Integer searchRadius = 2;

    private static final USSDSection thisSection = USSDSection.HOME;

    @Autowired
    private final UssdLocationServicesBroker ussdLocationServicesBroker;
    private final ObjectLocationBroker objectLocationBroker;
    private final EventBroker eventBroker;

    @Autowired
    public USSDAdvancedHomeController(UssdLocationServicesBroker ussdLocationServicesBroker, ObjectLocationBroker objectLocationBroker,
                                      EventBroker eventBroker){
        this.ussdLocationServicesBroker = ussdLocationServicesBroker;
        this.objectLocationBroker = objectLocationBroker;
        this.eventBroker = eventBroker;
    }

    @RequestMapping(value = homePath + moreMenus + startMenu)
    @ResponseBody
    public Request moreOptions(@RequestParam String msisdn) throws URISyntaxException{
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "more", promptKey, user));
        ussdMenu.addMenuOption(safetyMenus + startMenu, getMessage(thisSection, "more", optionsKey + "safety", user));
        ussdMenu.addMenuOption(moreMenus + startMenu + "/near-me", getMessage(thisSection, "more", optionsKey + "publicmtgs", user));
        ussdMenu.addMenuOption(keyStart, getMessage(optionsKey + "back", user));
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = homePath + moreMenus + startMenu + "/near-me")
    @ResponseBody
    public Request getPublicMeetingsNearUser(@RequestParam(value = phoneNumber) String inputNumber,
                                             @RequestParam(required = false) boolean repeat) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        GeoLocation guessedLocation = objectLocationBroker.fetchBestGuessUserLocation(user.getUid());

        USSDMenu ussdMenu;

        if (guessedLocation != null) {
            List<ObjectLocation> listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUser(searchRadius, user, guessedLocation,"public");
            logger.info("Size of meetings array in home more controller= {}",listOfPublicMeetingsNearUser.size());
            ussdMenu = listOfPublicMeetingsNearUser.isEmpty() ?
                    haveLocationButNoMeetings(user, repeat) :
                    haveLocationAndMeetings(user, repeat, listOfPublicMeetingsNearUser);
        } else {
            ussdMenu = haveNoLocation(user, repeat);
        }
        return menuBuilder(ussdMenu);
    }

    private USSDMenu haveLocationAndMeetings(User user, boolean repeat, List<ObjectLocation> publicMeetings) {
        final USSDMenu ussdMenu = new USSDMenu(repeat ? "Now we found some meetings:" :
                getMessage(thisSection, "public", promptKey + ".list", user));
        publicMeetings.forEach((ObjectLocation pm) -> {
            ussdMenu.addMenuOption(moreMenus + "/public-meeting/details?meetingUid=" +pm.getUid(),
                    pm.getDescription());
        });
        ussdMenu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
        return ussdMenu;
    }

    private USSDMenu haveLocationButNoMeetings(User user, boolean repeat) {
        USSDMenu ussdMenu = new USSDMenu(repeat ? "Sorry we still couldn't find any public meetings" :
                getMessage(thisSection, "public", promptKey + ".none", user));
        if (!repeat) {
            addTryTrackMeOptions(ussdMenu, user);
        } else {
            ussdMenu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
        }
        return ussdMenu;
    }

    private USSDMenu haveNoLocation(User user, boolean repeat) {
        USSDMenu ussdMenu = new USSDMenu(repeat ? "Sorry we still don't know where you are" :
                getMessage(thisSection, "public", promptKey + ".nolocation", user));
        if (!repeat) {
            addTryTrackMeOptions(ussdMenu, user);
        } else {
            // add only back option
            addBackOption(ussdMenu,user);
        }
        return ussdMenu;
    }

    private void addTryTrackMeOptions(USSDMenu menu, User user) {
        menu.addMenuOption(moreMenus + startMenu + "/track-me","Check again trying to use my location");
        // ussdMenu.addMenuOption(moreMenus + startMenu + "/track-me","Track me");
        menu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
    }

    private void addBackOption(USSDMenu ussdMenu,User user) {
        // todo : create
        ussdMenu.addMenuOption("more/start",getMessage(optionsKey + "back", user));
    }

    /*
        Method to display meeting details
    */
    @RequestMapping(value = homePath + moreMenus + startMenu + "/meeting-details")
    @ResponseBody
    public Request meetingDetails(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam String meetingUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        Meeting meeting = (Meeting) eventBroker.load(meetingUid);
        logger.info("Meeting UID = {}",meetingUid);

        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "public", promptKey + ".details", new String[] {
                meeting.getName(), meeting.getDeadlineTime().toString(), meeting.getEventLocation()
        }, user));

        ussdMenu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = homePath + moreMenus + startMenu + "/track-me")
    @ResponseBody
    public Request trackMe(@RequestParam(value = phoneNumber) String inputNumber)throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        boolean tracking = ussdLocationServicesBroker.addUssdLocationLookupAllowed(user.getUid(), UserInterfaceType.USSD);
        USSDMenu menu;
        if (tracking) {
            menu = new USSDMenu("Great, it looks like we can work out your location, let's try searching meetings again");
            menu.addMenuOption("/near-me?repeat=true", "Look for meetings");
            menu.addMenuOption("more/start", "No");
            ussdLocationServicesBroker.asyncUssdLocationLookupAndStorage(user.getUid());
        } else {
            menu = new USSDMenu("Sorry, we aren't able to determine your location");

            menu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
            menu.addMenuOption(startMenu,"Home");
            menu.addMenuOption("exit", "Exit");
        }
        return menuBuilder(menu);
    }

}
