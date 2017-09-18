package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

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

    @Autowired
    private UssdLocationServicesBroker ussdLocationServicesBroker;

    @Autowired
    private ObjectLocationBroker objectLocationBroker;


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
    public Request getPublicMeetingsNearUser(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);

        USSDMenu ussdMenu = new USSDMenu("Meetings Near you");

        GeoLocation location = ussdLocationServicesBroker.getUssdLocationForUser(user.getUid());
        //Check if is not null
        List<ObjectLocation> listOfPublicMeetingsNearUser = new ArrayList<>();
        if(location != null){
            //listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUser(location.getLatitude(),location.getLongitude(),2,user);
            listOfPublicMeetingsNearUser = objectLocationBroker.fetchMeetingsNearUserUssd(searchRadius,user);
            logger.info("Size of meetings array = {}",listOfPublicMeetingsNearUser.size());
            if(listOfPublicMeetingsNearUser != null){
                if(listOfPublicMeetingsNearUser.size() == 0){
                    ussdMenu.setPromptMessage("No Meetings near you");
                    ussdMenu.addMenuOption("more/start","Back");
                }else{
                    for (ObjectLocation objectLocation:listOfPublicMeetingsNearUser) {
                        ussdMenu.addMenuOption("",objectLocation.getDescription());
                    }
                    ussdMenu.addMenuOption("more/start","Back");
                }
            }else{

            }
        }else{

        }

        return menuBuilder(ussdMenu);
        //return menuBuilder(new USSDMenu("Number of Public meeting near you" + listOfPublicMeetingsNearUser.size()));
    }
}
