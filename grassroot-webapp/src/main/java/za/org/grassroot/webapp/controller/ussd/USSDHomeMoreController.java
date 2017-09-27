package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.geo.ObjectLocationBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Controller for the USSD menu more option
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDHomeMoreController extends USSDController{


    private static final Logger logger = LoggerFactory.getLogger(USSDMeetingController.class);
    private static final USSDSection thisSection = USSDSection.MORE;
    private static final  String keyStart = "start";
    private static final Integer searchRadius = 2;


    private UssdLocationServicesBroker ussdLocationServicesBroker;


    private ObjectLocationBroker objectLocationBroker;

    @Autowired
    public USSDHomeMoreController(UssdLocationServicesBroker ussdLocationServicesBroker,ObjectLocationBroker objectLocationBroker){
        this.ussdLocationServicesBroker = ussdLocationServicesBroker;
        this.objectLocationBroker = objectLocationBroker;
    }


    @RequestMapping(value = homePath + moreMenus + startMenu)
    @ResponseBody
    public  Request moreOptions() throws URISyntaxException{
        USSDMenu ussdMenu = new USSDMenu();

        ussdMenu.addMenuOption(safetyMenus + startMenu,"Safety group");
        ussdMenu.addMenuOption(moreMenus + startMenu + "/near-me","Public meetings near me");
        ussdMenu.addMenuOption(keyStart,"Back");
        return menuBuilder(ussdMenu);
    }

    @RequestMapping(value = homePath + moreMenus + startMenu + "/near-me")
    @ResponseBody
    public Request getPublicMeetingsNearUser(Model model, @RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);

        USSDMenu ussdMenu = new USSDMenu(messageSource.getMessage("ussd.info.public.meetings",null,new Locale("en")));

        GeoLocation location = ussdLocationServicesBroker.getUssdLocationForUser(user.getUid());
        //Check if is not null
        List<ObjectLocation> listOfPublicMeetingsNearUser = new ArrayList<>();
        if(location != null){
            //listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUser(-26.1934863,28.036417399999998,10,user);
            listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUserUssd(searchRadius,user);
            logger.info("Size of meetings array in home more Controller= {}",listOfPublicMeetingsNearUser.size());
            if(listOfPublicMeetingsNearUser != null){
                if(listOfPublicMeetingsNearUser.size() == 0){
                    ussdMenu.setPromptMessage(messageSource.getMessage("ussd.error.public.meetings",null,new Locale("en")));
                    ussdMenu.addMenuOption("more/start","Back");
                }else{

                    for (ObjectLocation objectLocation:listOfPublicMeetingsNearUser) {
                        ussdMenu.addMenuOption(moreMenus + startMenu + "/meeting-details",objectLocation.getDescription());
                    }
                    ussdMenu.addMenuOption("more/start","Back");
                }
            }else{
                throw new InvalidParameterException("Invalid location coordinates");
            }
        }else{
            throw new InvalidParameterException("Invalid location coordinates");
        }

        return menuBuilder(ussdMenu);
    }

    /*
        Method to display meeting details
    */
    @RequestMapping(value = homePath + moreMenus + startMenu + "/meeting-details")
    @ResponseBody
    public Request meetingDetails(@RequestParam(value = phoneNumber) String inputNumber)throws URISyntaxException {
        USSDMenu ussdMenu = new USSDMenu(messageSource.getMessage("ussd.meeting.details",null,new Locale("en")));

        ussdMenu.addMenuOption("more/start","Back");

        return menuBuilder(ussdMenu);
    }
}
