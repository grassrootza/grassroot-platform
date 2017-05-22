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
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.core.enums.LiveWireAlertType.INSTANT;
import static za.org.grassroot.core.enums.LiveWireAlertType.MEETING;
import static za.org.grassroot.webapp.enums.USSDSection.LIVEWIRE;

/**
 * Created by luke on 2017/05/07.
 */
@RequestMapping(path = "/ussd/livewire/", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDLiveWireController extends USSDController {

    private static final Logger logger = LoggerFactory.getLogger(USSDLiveWireController.class);
    private static final int listPageSize = 3;

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final DataSubscriberBroker dataSubscriberBroker;

    public USSDLiveWireController(LiveWireAlertBroker liveWireAlertBroker, DataSubscriberBroker dataSubscriberBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.dataSubscriberBroker = dataSubscriberBroker;
    }

    private String menuUri(String menu, String alertUid) {
        return "livewire/" + menu + (!StringUtils.isNullOrEmpty(alertUid) ? ("?alertUid=" + alertUid) : "");
    }

    private String uriForCache(String menu, String alertUid, String userInput) {
        return menuUri(menu, alertUid) + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
    }

    @RequestMapping("mtg")
    @ResponseBody
    public Request selectContactForMeeting(@RequestParam String msisdn,
                                           @RequestParam(required = false) String mtgUid,
                                           @RequestParam(required = false) String alertUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (StringUtils.isNullOrEmpty(alertUid)) {
            Objects.requireNonNull(mtgUid);
            alertUid = liveWireAlertBroker.create(user.getUid(), MEETING, mtgUid);
            cacheManager.putUssdMenuForUser(msisdn, menuUri("mtg", alertUid));
        }
        return menuBuilder(assembleContactChoiceMenu(user, alertUid, MEETING));
    }

    @RequestMapping("instant")
    @ResponseBody
    public Request selectGroupForInstantAlert(@RequestParam String msisdn,
                                              @RequestParam(required = false) Integer pageNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        List<Group> groups = liveWireAlertBroker.groupsForInstantAlert(user.getUid(), pageNumber, listPageSize);
        int numberGroups = (int) liveWireAlertBroker.countGroupsForInstantAlert(user.getUid());
        int page = (pageNumber == null) ? 0 : pageNumber;
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "instant", promptKey, user));
        groups.forEach(g -> {
            menu.addMenuOption("livewire/group?groupUid=" + g.getUid(), g.getName());
        });


        if (numberGroups > (page + 1) * listPageSize) {
            menu.addMenuOption("livewire/instant?pageNumber=" + (page + 1), getMessage("options.more", user));
        }

        menu.addMenuOption("start_livewire?page=0", getMessage("options.back", user));

        return menuBuilder(menu);
    }

    @RequestMapping("register")
    @ResponseBody
    public Request promptToRegisterAsContact(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "register", promptKey, user));
        menu.addMenuOption("livewire/register/do?location=true",
                getMessage(LIVEWIRE, "register", optionsKey + "location", user));
        menu.addMenuOption("livewire/register/do?location=false",
                getMessage(LIVEWIRE, "register", optionsKey + "nolocation", user));
        menu.addMenuOption("start_livewire?page=0", getMessage("options.back", user));
        return menuBuilder(menu);
    }

    @RequestMapping("register/do")
    @ResponseBody
    public Request registerAsLiveWireContact(@RequestParam String msisdn,
                                             @RequestParam boolean location) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        liveWireAlertBroker.updateUserLiveWireContactStatus(user.getUid(), true, UserInterfaceType.USSD);
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "register.do", promptKey, user));
        menu.addMenuOption("start_livewire?page=0",
                getMessage(LIVEWIRE, "register.do", optionsKey + "lwire", user));
        menu.addMenuOptions(optionsHomeExit(user, false));
        if (location) {
            liveWireAlertBroker.trackLocationForLiveWireContact(user.getUid(), UserInterfaceType.USSD);
        }
        return menuBuilder(menu);
    }

    @RequestMapping("group")
    public Request groupChosen(@RequestParam String msisdn,
                               @RequestParam(required = false) String groupUid,
                               @RequestParam(required = false) String alertUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (StringUtils.isNullOrEmpty(alertUid)) {
            alertUid = liveWireAlertBroker.create(user.getUid(), INSTANT, groupUid);
            cacheManager.putUssdMenuForUser(msisdn, menuUri("group", alertUid));
        }
        return menuBuilder(assembleContactChoiceMenu(user, alertUid, INSTANT));
    }

    private USSDMenu assembleContactChoiceMenu(User user, String alertUid, LiveWireAlertType type) {
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "contact", promptKey, user));
        menu.addMenuOption(menuUri("description", alertUid) + "&contactUid=" + user.getUid(),
                getMessage(LIVEWIRE, "contact", optionsKey + "me", new String[] {user.getName()}, user));
        menu.addMenuOption(menuUri("contact/name", alertUid) + "&contactUid=" + user.getUid(),
                getMessage(LIVEWIRE, "contact", optionsKey + "me.change", user));

        // for the moment, leaving out the same for group (all organizers), though todo next
        if (MEETING.equals(type)) {
            LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
            User mtgOrganizer = alert.getMeeting().getCreatedByUser();
            if (!mtgOrganizer.equals(user)) {
                menu.addMenuOption(menuUri("contact/name", alertUid) + "&contactUid=" + mtgOrganizer.getUid(),
                        getMessage(LIVEWIRE, "contact", optionsKey + "mtg.org", user));
            }
        }
        menu.addMenuOption(menuUri("contact/phone", alertUid),
                getMessage(LIVEWIRE, "contact", optionsKey + "other", user));
        return menu;
    }

    @RequestMapping("contact/phone")
    @ResponseBody
    public Request enterContactPersonNumber(@RequestParam String msisdn,
                                            @RequestParam String alertUid,
                                            @RequestParam(required = false) Boolean revising) throws URISyntaxException {
        final String revisingSuffix = revising != null ? "&revising=1" : "";
        User user = userManager.findByInputNumber(msisdn, menuUri("contact/phone", alertUid) + revisingSuffix);
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "contact.phone", promptKey, user));
        menu.setFreeText(true);
        menu.setNextURI(menuUri("contact/name", alertUid) + revisingSuffix);
        return menuBuilder(menu);
    }

    @RequestMapping("contact/name")
    @ResponseBody
    public Request enterContactPersonName(@RequestParam String msisdn,
                                          @RequestParam String alertUid,
                                          @RequestParam String request,
                                          @RequestParam(required = false) String priorInput,
                                          @RequestParam(required = false) String contactUid,
                                          @RequestParam(required = false) Boolean revising) throws URISyntaxException {
        final String revisingSuffix = revising == null ? "" : "&revising=1";
        final String menuRoot = menuUri("contact/name", alertUid) + revisingSuffix;
        final String userInput = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
        User sessionUser = userManager.findByInputNumber(msisdn);

        if (StringUtils.isNullOrEmpty(contactUid) && !PhoneNumberUtil.testInputNumber(userInput)) {
            USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "contact.phone", "error", sessionUser));
            menu.setFreeText(true);
            menu.setNextURI(menuUri("contact/name", alertUid) + revisingSuffix);
            cacheManager.putUssdMenuForUser(msisdn, uriForCache("contact/name", alertUid, userInput) + revisingSuffix);
            return menuBuilder(menu);
        } else {
            User contactUser = StringUtils.isNullOrEmpty(contactUid) ?
                    userManager.loadOrCreateUser(request) : userManager.load(contactUid);
            cacheManager.putUssdMenuForUser(msisdn, menuRoot + "&contactUid=" + contactUser.getUid());
            liveWireAlertBroker.updateContactUser(sessionUser.getUid(), alertUid,
                    contactUser.getUid(), contactUser.getDisplayName());
            final String prompt = contactUser.hasName() ?
                    getMessage(LIVEWIRE, "contact.name", promptKey, new String[] { contactUser.getName() }, sessionUser) :
                    getMessage(LIVEWIRE, "contact.noname", promptKey, sessionUser);
            USSDMenu menu = new USSDMenu(prompt);
            menu.setFreeText(true);
            if (revising == null || !revising) {
                menu.setNextURI(menuUri("description", alertUid) + "&contactUid=" + contactUser.getUid());
            } else {
                menu.setNextURI(menuUri("confirm", alertUid) + "&contactUid=" + contactUser.getUid() +
                        "&revisingContact=true");
            }
            return menuBuilder(menu);
        }
    }

    @RequestMapping("description")
    @ResponseBody
    public Request enterDescription(@RequestParam String msisdn, @RequestParam String alertUid,
                                    @RequestParam String request,
                                    @RequestParam(required = false) String contactUid,
                                    @RequestParam(required = false) String priorInput) throws URISyntaxException {
        String userInput = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
        User user = userManager.findByInputNumber(msisdn, uriForCache("description", alertUid, userInput)
                + (StringUtils.isNullOrEmpty(contactUid) ? "" : "&contactUid=" + contactUid));
        if (!StringUtils.isNullOrEmpty(contactUid)) {
            liveWireAlertBroker.updateContactUser(user.getUid(), alertUid, contactUid,
                    StringUtils.isNumber(userInput) ? null : userInput);
        }
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "description", promptKey, user));
        menu.setFreeText(true);
        menu.setNextURI(menuUri("confirm", alertUid));
        return menuBuilder(menu);
    }

    @RequestMapping("confirm")
    @ResponseBody
    public Request confirmAlert(@RequestParam String msisdn, @RequestParam String alertUid,
                                @RequestParam String request,
                                @RequestParam(required = false) String priorInput,
                                @RequestParam(required = false) Boolean revisingContact,
                                @RequestParam(required = false) String contactUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (revisingContact == null || !revisingContact) {
            String description = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
            cacheManager.putUssdMenuForUser(msisdn, uriForCache("confirm", alertUid, description));
            liveWireAlertBroker.updateDescription(user.getUid(), alertUid, description);
        } else {
            Objects.requireNonNull(contactUid);
            String contactName = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
            cacheManager.putUssdMenuForUser(msisdn, uriForCache("confirm", alertUid, contactName) +
                "&revisingContact=true&contactUid=" + contactUid);
            if (!StringUtils.isNumber(contactName)) {
                liveWireAlertBroker.updateContactUser(user.getUid(), alertUid, contactUid, contactName);
            }
        }
        LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
        String[] fields = new String[] { alert.getDescription(), alert.getContactNameNullSafe() };

        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "confirm", promptKey, fields, user));
        menu.addMenuOption(menuUri("send", alertUid) + "&location=false",
                getMessage(LIVEWIRE, "confirm", optionsKey + "send", user));
        menu.addMenuOption(menuUri("send", alertUid) + "&location=true",
                getMessage(LIVEWIRE, "confirm", optionsKey + "location", user));
        menu.addMenuOption(menuUri("description", alertUid),
                getMessage(LIVEWIRE, "confirm", optionsKey + "description", user));
        menu.addMenuOption(menuUri("contact/phone", alertUid) + "&revising=1",
                getMessage(LIVEWIRE, "confirm", optionsKey + "contact", user));
        return menuBuilder(menu);
    }

    @RequestMapping("send")
    @ResponseBody
    public Request sendAlert(@RequestParam String msisdn, @RequestParam String alertUid,
                             @RequestParam boolean location) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, null);
        Instant sendTime = location ? Instant.now().plus(3, ChronoUnit.MINUTES) : Instant.now();
        liveWireAlertBroker.setAlertComplete(user.getUid(), alertUid, sendTime); // say, will be reviewed
        if (location) {
            liveWireAlertBroker.addLocationToAlert(user.getUid(), alertUid, null, UserInterfaceType.USSD);
        }
        LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "send", alert.getType().name().toLowerCase() + ".success",
                new String[] { String.valueOf(dataSubscriberBroker.countPushEmails()) }, user));
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menuBuilder(menu);
    }

}
