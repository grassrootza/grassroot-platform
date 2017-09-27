package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
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

    @Autowired
    public USSDAdvancedHomeController(UssdLocationServicesBroker ussdLocationServicesBroker, ObjectLocationBroker objectLocationBroker, EventBroker eventBroker){
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
    public Request getPublicMeetingsNearUser(Model model, @RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);

        USSDMenu ussdMenu = new USSDMenu();

        GeoLocation location = ussdLocationServicesBroker.getUssdLocationForUser(user.getUid());
        //Check if is not null
        if (location != null){
            //listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUser(-26.1934863,28.036417399999998,10,user);
            List<ObjectLocation> listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUserUssd(searchRadius, user);
            logger.info("Size of meetings array in home more controller= {}",listOfPublicMeetingsNearUser.size());
            if (listOfPublicMeetingsNearUser.isEmpty()){
                ussdMenu.setPromptMessage(getMessage(thisSection, "public", promptKey + ".none", user));
                // todo: add an option "check again trying to use my location" - then use USSD Location Broker
                ussdMenu.addMenuOption("more/start", getMessage(optionsKey + "back", user));
            } else {
                ussdMenu.setPromptMessage(getMessage(thisSection, "public", promptKey + ".list", user));
                listOfPublicMeetingsNearUser.forEach(pm -> {
                    ussdMenu.addMenuOption(moreMenus + "/public-meeting/details?meetingUid=" +pm.getUid(),
                            pm.getDescription());
                });
            }
        } else {
            // todo : request permission to track them, and try track them
            ussdMenu.setPromptMessage(getMessage(thisSection, "public", promptKey + ".nolocation", user));
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
}
