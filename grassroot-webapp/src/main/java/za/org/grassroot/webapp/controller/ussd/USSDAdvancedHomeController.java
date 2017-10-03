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
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.repository.UserLocationLogRepository;
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

    private final UssdLocationServicesBroker ussdLocationServicesBroker;
    private final ObjectLocationBroker objectLocationBroker;
    private final EventBroker eventBroker;
    private final UserLocationLogRepository userLocationLogRepository;

    @Autowired
    public USSDAdvancedHomeController(UssdLocationServicesBroker ussdLocationServicesBroker, ObjectLocationBroker objectLocationBroker,
                                      EventBroker eventBroker,UserLocationLogRepository userLocationLogRepository){
        this.ussdLocationServicesBroker = ussdLocationServicesBroker;
        this.objectLocationBroker = objectLocationBroker;
        this.eventBroker = eventBroker;
        this.userLocationLogRepository = userLocationLogRepository;
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
    public Request getPublicMeetingsNearUser(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);

        USSDMenu ussdMenu = new USSDMenu();

        GeoLocation location = ussdLocationServicesBroker.getUssdLocationForUser(user.getUid());

        if (location != null){
            List<ObjectLocation> listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUser(searchRadius,user,location);
            logger.info("Size of meetings array in home more controller= {}",listOfPublicMeetingsNearUser.size());
            if (listOfPublicMeetingsNearUser.isEmpty()){
                ussdMenu.setPromptMessage(getMessage(thisSection, "public", promptKey + ".none", user));
                // todo: add an option "check again trying to use my location" - then use USSD Location Broker
                ussdMenu.addMenuOption(moreMenus + startMenu + "/use-my-location","Check again trying to use my location");
                ussdMenu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
            } else {
                ussdMenu.setPromptMessage(getMessage(thisSection, "public", promptKey + ".list", user));
                listOfPublicMeetingsNearUser.forEach(pm -> ussdMenu.addMenuOption(moreMenus + "/public-meeting/details?meetingUid=" +pm.getUid(),
                        pm.getDescription()));
            }
        } else {
            // todo : request permission to track them, and try track them
            ussdMenu.setPromptMessage(getMessage(thisSection, "public", promptKey + ".nolocation", user));
            ussdMenu.addMenuOption(moreMenus + startMenu + "/track-me","Track me");
        }

        ussdMenu.addMenuOption(moreMenus + startMenu,"Back");
        return menuBuilder(ussdMenu);
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

    @RequestMapping(value = homePath + moreMenus + startMenu + "/use-my-location")
    @ResponseBody
    public Request tryUsingMyLocation(@RequestParam(value = phoneNumber) String inputNumber)throws URISyntaxException{
        USSDMenu ussdMenu = new USSDMenu();
        User user = userManager.findByInputNumber(inputNumber);


        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = homePath + moreMenus + startMenu + "/track-me")
    @ResponseBody
    public Request trackMe(@RequestParam(value = phoneNumber) String inputNumber)throws URISyntaxException{
        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu ussdMenu = new USSDMenu(getMessage(thisSection, "start", optionsKey + "track", user));
        ussdMenu.addMenuOption(moreMenus + startMenu + "/confirm",getMessage(optionsKey + "confirm",user));
        ussdMenu.addMenuOption("more/start",getMessage(optionsKey + "cancel",user));

        return menuBuilder(ussdMenu);
    }
}
