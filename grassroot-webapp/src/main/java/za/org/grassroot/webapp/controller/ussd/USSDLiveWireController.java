package za.org.grassroot.webapp.controller.ussd;

import lombok.extern.slf4j.Slf4j;
import org.h2.util.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.DataSubscriber;
import za.org.grassroot.core.domain.livewire.LiveWireAlert;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.LiveWireAlertDestType;
import za.org.grassroot.core.enums.LiveWireAlertType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.services.livewire.DataSubscriberBroker;
import za.org.grassroot.services.livewire.LiveWireAlertBroker;
import za.org.grassroot.services.livewire.LiveWireContactBroker;
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
@Slf4j
@RestController
@RequestMapping(path = "/ussd/livewire/", method = GET, produces = MediaType.APPLICATION_XML_VALUE)
public class USSDLiveWireController extends USSDBaseController {

    private static final int listPageSize = 3;

    private final LiveWireAlertBroker liveWireAlertBroker;
    private final LiveWireContactBroker liveWireContactBroker;
    private final DataSubscriberBroker dataSubscriberBroker;

    public USSDLiveWireController(LiveWireAlertBroker liveWireAlertBroker,
                                  LiveWireContactBroker liveWireContactBroker,
                                  DataSubscriberBroker dataSubscriberBroker) {
        this.liveWireAlertBroker = liveWireAlertBroker;
        this.liveWireContactBroker = liveWireContactBroker;
        this.dataSubscriberBroker = dataSubscriberBroker;
    }

    private String menuUri(String menu, String alertUid) {
        return "livewire/" + menu + (!StringUtils.isNullOrEmpty(alertUid) ? ("?alertUid=" + alertUid) : "");
    }

    private String uriForCache(String menu, String alertUid, String userInput) {
        return menuUri(menu, alertUid) + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
    }

    @RequestMapping(value = homePath + startMenu + "_livewire")
    @ResponseBody
    public Request liveWirePageMenu(@RequestParam String msisdn, @RequestParam int page) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        return menuBuilder(assembleLiveWireOpening(user, page));
    }

    protected USSDMenu assembleLiveWireOpening(User user, int page) {
        long startTime = System.currentTimeMillis();
        long groupsForInstant = liveWireAlertBroker.countGroupsForInstantAlert(user.getUid());
        List<Meeting> meetingList = liveWireAlertBroker.meetingsForAlert(user.getUid());

        log.info("Generating LiveWire menu, groups for instant alert {}, meetings {}, took {} msecs",
                groupsForInstant, meetingList.size(), System.currentTimeMillis() - startTime);

        USSDMenu menu;
        if (groupsForInstant == 0L && meetingList.isEmpty()) {
            menu = new USSDMenu(getMessage(LIVEWIRE, startMenu, "prompt.nomeetings", user));
            menu.addMenuOption(meetingMenus + startMenu + "?newMtg=1", "Create a meeting");
            menu.addMenuOption(startMenu, "Main menu");
            menu.addMenuOption("exit", "Exit");
        } else if (meetingList.isEmpty()) {
            menu = new USSDMenu(getMessage(LIVEWIRE, startMenu, "prompt.instant.only", user));
            menu.addMenuOption("livewire/instant", getMessage(LIVEWIRE, startMenu, optionsKey + "instant", user));
            menu.addMenuOption(meetingMenus + startMenu + "?newMtg=1", getMessage(LIVEWIRE, startMenu, optionsKey + "mtg.create", user));
            menu.addMenuOption(startMenu, getMessage(LIVEWIRE, startMenu, optionsKey + "home", user));
        } else {
            final String prompt = groupsForInstant != 0L ?
                    getMessage(LIVEWIRE, startMenu, "prompt.meetings.only", user) :
                    getMessage(LIVEWIRE, startMenu, "prompt.both", user);
            menu = new USSDMenu(prompt);

            int pageLimit = page == 0 ? 2 : (page + 1) * 3 - 1; // because of opening page lower chars
            int pageStart = page == 0 ? 0 : (page * 3) - 1;
            for (int i = pageStart; i < pageLimit && i < meetingList.size(); i++) {
                Meeting meeting = meetingList.get(i);
                String[] fields = new String[] {
                        trimMtgName(meeting.getName()),
                        meeting.getEventDateTimeAtSAST().format(shortDateFormat) };
                menu.addMenuOption("livewire/mtg?mtgUid=" + meeting.getUid(),
                        getMessage(LIVEWIRE, startMenu, optionsKey + "meeting", fields, user));
            }

            if (pageLimit < meetingList.size()) {
                menu.addMenuOption(startMenu + "_livewire?page=" + (page + 1), getMessage("options.more", user));
            }

            if (page > 0) {
                menu.addMenuOption(startMenu + "_livewire?page=" + (page - 1), getMessage("options.back", user));
            }
        }

        if (groupsForInstant != 0L) {
            menu.addMenuOption("livewire/instant", getMessage(LIVEWIRE, startMenu, optionsKey + "instant", user));
            if (!user.isLiveWireContact()) {
                menu.addMenuOption("livewire/register", getMessage(LIVEWIRE, startMenu, optionsKey + "register", user));
            }
        }
        return menu;
    }

    private String trimMtgName(String name) {
        return name.length() < 20 ? name : name.substring(0, 20) + "...";
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
        liveWireContactBroker.updateUserLiveWireContactStatus(user.getUid(), true, UserInterfaceType.USSD);
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "register.do", promptKey, user));
        menu.addMenuOption("start_livewire?page=0",
                getMessage(LIVEWIRE, "register.do", optionsKey + "lwire", user));
        menu.addMenuOptions(optionsHomeExit(user, false));
        if (location) {
            liveWireContactBroker.trackLocationForLiveWireContact(user.getUid(), UserInterfaceType.USSD);
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
        menu.addMenuOption(menuUri("headline", alertUid) + "&contactUid=" + user.getUid(),
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
                menu.setNextURI(menuUri("headline", alertUid) + "&contactUid=" + contactUser.getUid());
            } else {
                menu.setNextURI(menuUri("confirm", alertUid) + "&contactUid=" + contactUser.getUid() +
                        "&revisingContact=true&field=contact");
            }
            return menuBuilder(menu);
        }
    }

    @RequestMapping("headline")
    @ResponseBody
    public Request enterDescription(@RequestParam String msisdn, @RequestParam String alertUid,
                                    @RequestParam String request,
                                    @RequestParam(required = false) String contactUid,
                                    @RequestParam(required = false) String priorInput) throws URISyntaxException {
        String userInput = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
        User user = userManager.findByInputNumber(msisdn, uriForCache("headline", alertUid, userInput)
                + (StringUtils.isNullOrEmpty(contactUid) ? "" : "&contactUid=" + contactUid));
        if (!StringUtils.isNullOrEmpty(contactUid)) {
            liveWireAlertBroker.updateContactUser(user.getUid(), alertUid, contactUid,
                    StringUtils.isNumber(userInput) ? null : userInput);
        }
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "headline", promptKey, user));
        menu.setFreeText(true);
        menu.setNextURI(menuUri(dataSubscriberBroker.doesUserHaveCustomLiveWireList(user.getUid()) ?
                "destination" : "confirm", alertUid) + "&field=headline");
        return menuBuilder(menu);
    }

    @RequestMapping("destination")
    @ResponseBody
    public Request chooseList(@RequestParam String msisdn, @RequestParam String alertUid,
                              @RequestParam String request,
                              @RequestParam(required = false) String priorInput) throws URISyntaxException {
        String userInput = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
        User user = userManager.findByInputNumber(msisdn, uriForCache("destination", alertUid, userInput));
        liveWireAlertBroker.updateHeadline(user.getUid(), alertUid, userInput);
        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "destination", promptKey, user));
        // make this either "general", or "just us"
        final String uriBase = menuUri("confirm", alertUid) + "&field=destination";
        final DataSubscriber destination = dataSubscriberBroker.fetchLiveWireListForSubscriber(user.getUid());
        menu.addMenuOption(uriBase + "&destType=GENERAL", getMessage(LIVEWIRE, "destination", optionsKey + "general", user));
        menu.addMenuOption(uriBase + "&destType=SINGLE_LIST&destinationUid=" + destination.getUid(),
                getMessage(LIVEWIRE, "destination", optionsKey + "ownlist", destination.getDisplayName(), user));
        menu.addMenuOption(uriBase + "&destType=BOTH&destinationUid=" + destination.getUid(),
                getMessage(LIVEWIRE, "destination", optionsKey + "both", user));
        return menuBuilder(menu);
    }

    @RequestMapping("confirm")
    @ResponseBody
    public Request confirmAlert(@RequestParam String msisdn, @RequestParam String alertUid,
                                @RequestParam String request,
                                @RequestParam String field,
                                @RequestParam(required = false) String priorInput,
                                @RequestParam(required = false) String destType,
                                @RequestParam(required = false) String destinationUid,
                                @RequestParam(required = false) Boolean revisingContact,
                                @RequestParam(required = false) String contactUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);

        log.info("field on alert confirmation: {}", field);
        if ("headline".equals(field)) {
            String headline = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
            cacheManager.putUssdMenuForUser(msisdn, uriForCache("confirm", alertUid, headline) + "&field=" + field);
            liveWireAlertBroker.updateHeadline(user.getUid(), alertUid, headline);
        } else if ("contact".equals(field)) {
            Objects.requireNonNull(contactUid);
            String contactName = StringUtils.isNullOrEmpty(priorInput) ? request : priorInput;
            cacheManager.putUssdMenuForUser(msisdn, uriForCache("confirm", alertUid, contactName) +
                    "&field=" + field + "&revisingContact=true&contactUid=" + contactUid);
            if (!StringUtils.isNumber(contactName)) {
                liveWireAlertBroker.updateContactUser(user.getUid(), alertUid, contactUid, contactName);
            }
        } else if ("destination".equals(field)) {
            cacheManager.putUssdMenuForUser(msisdn, uriForCache("confirm", alertUid, destType) +
                    "&field=" + field + "&destType=" + destType + (destinationUid != null ? "&destinationUid=" + destinationUid : ""));
            LiveWireAlertDestType dest = "BOTH".equals(destType) ? LiveWireAlertDestType.SINGLE_AND_PUBLIC :
                    "SINGLE_LIST".equals(destType) ? LiveWireAlertDestType.SINGLE_LIST : LiveWireAlertDestType.PUBLIC_LIST;
            liveWireAlertBroker.updateAlertDestination(user.getUid(), alertUid, destinationUid, dest);
        }

        LiveWireAlert alert = liveWireAlertBroker.load(alertUid);
        String[] fields = new String[] { alert.getHeadline(), alert.getContactNameNullSafe() };

        USSDMenu menu = new USSDMenu(getMessage(LIVEWIRE, "confirm", promptKey, fields, user));
        menu.addMenuOption(menuUri("send", alertUid) + "&location=false",
                getMessage(LIVEWIRE, "confirm", optionsKey + "send", user));
        menu.addMenuOption(menuUri("send", alertUid) + "&location=true",
                getMessage(LIVEWIRE, "confirm", optionsKey + "location", user));
        menu.addMenuOption(menuUri("headline", alertUid),
                getMessage(LIVEWIRE, "confirm", optionsKey + "headline", user));
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
                new String[] { String.valueOf(dataSubscriberBroker.countPushEmails(alert)) }, user));
        menu.addMenuOptions(optionsHomeExit(user, false));
        return menuBuilder(menu);
    }

}
