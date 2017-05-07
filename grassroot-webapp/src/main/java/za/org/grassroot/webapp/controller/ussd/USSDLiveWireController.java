package za.org.grassroot.webapp.controller.ussd;

import org.h2.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.core.enums.LiveWireAlertType.INSTANT;
import static za.org.grassroot.core.enums.LiveWireAlertType.MEETING;

/**
 * Created by luke on 2017/05/07.
 */
@RequestMapping(path = "/ussd/livewire/", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDLiveWireController extends USSDController {

    private static final Logger logger = LoggerFactory.getLogger(USSDLiveWireController.class);
    private static final int listPageSize = 3;

    private final LiveWireAlertBroker liveWireAlertBroker;

    public USSDLiveWireController(LiveWireAlertBroker liveWireAlertBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
    }

    private String menuUri(String menu, String alertUid) {
        return "livewire/" + menu + (!StringUtils.isNullOrEmpty(alertUid) ? ("?alertUid=" + alertUid) : "");
    }

    @RequestMapping("mtg")
    @ResponseBody
    public Request selectContactForMeeting(@RequestParam String msisdn, @RequestParam String mtgUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        String alertUid = liveWireAlertBroker.create(user.getUid(), MEETING, mtgUid);
        return menuBuilder(assembleContactChoiceMenu(user, alertUid, MEETING));
    }

    @RequestMapping("instant")
    @ResponseBody
    public Request selectGroupForInstantAlert(@RequestParam String msisdn, @RequestParam(required = false) Integer pageNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        List<Group> groups = liveWireAlertBroker.groupsForInstantAlert(user.getUid(), pageNumber, listPageSize);
        int numberGroups = (int) liveWireAlertBroker.countGroupsForInstantAlert(user.getUid());
        int page = (pageNumber == null) ? 0 : pageNumber;
        USSDMenu menu = new USSDMenu("What group is this instant alert related to?");
        groups.forEach(g -> {
            menu.addMenuOption("livewire/group?groupUid=" + g.getUid(), g.getName());
        });
        if (numberGroups > (page + 1) * listPageSize) {
            menu.addMenuOption("livewire/instant?pageNumber=" + (page + 1), "More");
        }
        return menuBuilder(menu);
    }

    @RequestMapping("group")
    public Request groupChosen(@RequestParam String msisdn, @RequestParam String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        String alertUid = liveWireAlertBroker.create(user.getUid(), INSTANT, groupUid);
        return menuBuilder(assembleContactChoiceMenu(user, alertUid, INSTANT));
    }

    private USSDMenu assembleContactChoiceMenu(User user, String alertUid, LiveWireAlertType type) {
        USSDMenu menu = new USSDMenu("Who is the contact person? You or someone else?");
        menu.addMenuOption(menuUri("description", alertUid) + "&contactUid=" + user.getUid(), "Me");
        // groups can have multiple organizers, etc., so for moment just leaving out this shortcut
        if (MEETING.equals(type)) {
            menu.addMenuOption(menuUri("description", alertUid) + "contact=ORG", "Meeting caller");
        }
        menu.addMenuOption(menuUri("contact/phone", alertUid), "Someone else");
        return menu;
    }

    @RequestMapping("contact/phone")
    @ResponseBody
    public Request enterContactPersonNumber(@RequestParam String msisdn, @RequestParam String alertUid) throws URISyntaxException {
        USSDMenu menu = new USSDMenu("Please enter the contact person's phone number:");
        menu.setFreeText(true);
        menu.setNextURI(menuUri("contact/name", alertUid));
        return menuBuilder(menu);
    }

    @RequestMapping("contact/name")
    @ResponseBody
    public Request enterContactPersonName(@RequestParam String msisdn, @RequestParam String alertUid,
                                          @RequestParam String request) throws URISyntaxException {
        // todo : add check for badly formatted input
        User sessionUser = userManager.findByInputNumber(msisdn);
        User contactUser = userManager.loadOrCreateUser(request);
        liveWireAlertBroker.updateContactUser(sessionUser.getUid(), alertUid,
                contactUser.getUid(), contactUser.getDisplayName());
        final String prompt = contactUser.hasName() ?
                "Is it this person: " + contactUser.getName() + "? Enter 0 if yes, otherwise correct the name" :
                "Please enter the contact person's name";
        USSDMenu menu = new USSDMenu(prompt);
        menu.setFreeText(true);
        menu.setNextURI(menuUri("description", alertUid) + "&contactUid=" + contactUser.getUid());
        return menuBuilder(menu);
    }

    @RequestMapping("description")
    @ResponseBody
    public Request enterDescription(@RequestParam String msisdn, @RequestParam String alertUid,
                                    @RequestParam String contactUid,
                                    @RequestParam(userInputParam) String contactName) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        liveWireAlertBroker.updateContactUser(user.getUid(), alertUid, contactUid, contactName);
        USSDMenu menu = new USSDMenu("Please add a description: ");
        menu.setFreeText(true);
        menu.setNextURI(menuUri("confirm", alertUid));
        return menuBuilder(menu);
    }

    @RequestMapping("confirm")
    @ResponseBody
    public Request confirmAlert(@RequestParam String msisdn, @RequestParam String alertUid,
                                @RequestParam(userInputParam) String description) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        liveWireAlertBroker.updateDescription(user.getUid(), alertUid, description);
        LiveWireAlert alert = liveWireAlertBroker.load(alertUid);

        USSDMenu menu = new USSDMenu("Going to send this sucker out now. Okay?");
        menu.addMenuOption(menuUri("send", alertUid) + "&location=false", "Send");
        menu.addMenuOption(menuUri("send", alertUid) + "&location=true", "Send, with my location");
        menu.addMenuOption(menuUri("description", alertUid), "Change description");
        menu.addMenuOption("contact", "Change contact"); // todo : add a back flag
        return menuBuilder(menu);
    }

    @RequestMapping("send")
    @ResponseBody
    public Request sendAlert(@RequestParam String msisdn, @RequestParam String alertUid,
                             @RequestParam boolean location) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        Instant sendTime = location ? Instant.now().plus(3, ChronoUnit.MINUTES) : Instant.now();
        liveWireAlertBroker.setAlertToSend(user.getUid(), alertUid, sendTime);
        if (location) {
            liveWireAlertBroker.addLocationToAlert(user.getUid(), alertUid, null, UserInterfaceType.USSD);
        }
        USSDMenu menu = new USSDMenu("Done! The world now knows");
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menuBuilder(menu);
    }

}
