package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventRequest;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.task.EventRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.enums.VoteTime;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;
import static za.org.grassroot.webapp.enums.VoteTime.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.backVoteUrl;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveVoteMenu;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDVoteController.class);

    @Value("${grassroot.events.limit.enabled:false}")
    private boolean eventMonthlyLimitActive;

    private static final int EVENT_LIMIT_WARNING_THRESHOLD = 5; // only warn when below this

    private final EventRequestBroker eventRequestBroker;
    private final PermissionBroker permissionBroker;
    private final AccountGroupBroker accountGroupBroker;

    private USSDEventUtil eventUtil;

    private static final String path = homePath + voteMenus;

    private static final String pathNew = homePath + voteMenus + "new_format/";
    private static final String menusNew = voteMenus + "new_format/";

    private static final USSDSection thisSection = USSDSection.VOTES;

    @Autowired
    public USSDVoteController(EventRequestBroker eventRequestBroker, PermissionBroker permissionBroker, AccountGroupBroker accountGroupBroker) {
        this.eventRequestBroker = eventRequestBroker;
        this.permissionBroker = permissionBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @Autowired
    public void setEventUtil(USSDEventUtil eventUtil) {
        this.eventUtil = eventUtil;
    }

    private String menuUrl(String menu, String requestUid) {
        return menusNew + menu + "?requestUid=" + requestUid;
    }

    /*
    Restructured menus begin here: begin with subject
     */
    @RequestMapping(value = pathNew + "subject")
    @ResponseBody
    public Request voteSubject(@RequestParam String msisdn) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, GROUP_PERMISSION_CREATE_GROUP_VOTE);
        USSDMenu menu;
        if (possibleGroups == 0) {
            // this will by definition return the "no group" menu, since we are in this branch
            menu = ussdGroupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection));
        } else {
            menu = new USSDMenu("Please enter a subject for the vote");
            menu.setFreeText(true);
            menu.setNextURI(menusNew + "type");
        }
        return menuBuilder(menu);
    }

    @RequestMapping(value = pathNew + "type")
    @ResponseBody
    public Request voteType(@RequestParam String msisdn, @RequestParam String request,
                            @RequestParam(required = false) String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (StringUtils.isEmpty(requestUid)) {
            requestUid = eventRequestBroker.createNewStyleEmptyVote(user.getUid(), request);
        }
        // todo: add a "revising" flag to the saved URL
        cacheManager.putUssdMenuForUser(msisdn, saveVoteMenu("type", requestUid));
        USSDMenu menu = new USSDMenu("What kind of vote will it be?");
        menu.addMenuOption(menuUrl("yes_no", requestUid), "Yes or no vote");
        menu.addMenuOption(menuUrl("multi_option/start", requestUid), "Write my own options");
        return menuBuilder(menu);
    }

    @RequestMapping(value = pathNew + "yes_no")
    @ResponseBody
    public Request voteType(@RequestParam String msisdn, @RequestParam String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, GROUP_PERMISSION_CREATE_GROUP_VOTE);
        USSDMenu menu = possibleGroups == 1 ?
                timeMenu(user, "Creating yes-no vote for your group. When will it close?", requestUid) :
                ussdGroupUtil.askForGroup(new USSDGroupUtil
                        .GroupMenuBuilder(user, thisSection)
                        .messageKey("group")
                        .urlForExistingGroup("closing?requestUid=" + requestUid));
        return menuBuilder(menu);
    }

    @RequestMapping(value = pathNew + "closing")
    @ResponseBody
    public Request selectTime(@RequestParam String msisdn, @RequestParam String requestUid,
                                @RequestParam String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        eventRequestBroker.updateVoteGroup(user.getUid(), requestUid, groupUid);
        return menuBuilder(timeMenu(user, "Okay, now just choose when the vote will close", requestUid));
    }

    @RequestMapping(value = pathNew + "multi_option/start")
    @ResponseBody
    public Request initiateMultiOption(@RequestParam String msisdn, @RequestParam String requestUid)
            throws URISyntaxException {
        USSDMenu menu = new USSDMenu("Okay, we will set the options one by one. Please enter the first option");
        menu.setFreeText(true);
        menu.setNextURI(menuUrl("multi_option/add", requestUid));
        return menuBuilder(menu);
    }

    @RequestMapping(value = pathNew + "multi_option/add")
    @ResponseBody
    public Request addVoteOption(@RequestParam String msisdn, @RequestParam String requestUid,
                                 @RequestParam String request,
                                 @RequestParam(required = false) String priorInput) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        // watch for duplication but service & core should both catch it
        String userInput = StringUtils.isEmpty(priorInput) ? request : priorInput;
        int numberOptions = eventRequestBroker.load(requestUid).getVoteOptions().size();
        if (numberOptions > 1 && "0".equals(userInput.trim())) {
            return menuBuilder(timeMenu(user, "Okay, vote options set, now please add a time", requestUid));
        } else {
            int newNumber = eventRequestBroker.addVoteOption(user.getUid(), requestUid, userInput);
            final String prompt = newNumber > 1 ? "Enter another option or 0 to end": "Enter another option";
            USSDMenu menu = new USSDMenu(prompt, menuUrl("multi_option/add", requestUid));
            return menuBuilder(menu);
        }
    }

    private USSDMenu timeMenu(User user, String prompt, String requestUid) {
        USSDMenu menu = new USSDMenu(prompt);

        String nextUrl = menusNew + "confirm?requestUid=" + requestUid + "&field=standard&time=";
        String optionKey = voteKey + ".time." + optionsKey;

        menu.addMenuOption(nextUrl + INSTANT.name(), getMessage(optionKey + "instant", user));
        menu.addMenuOption(nextUrl + HOUR.name(), getMessage(optionKey + "hour", user));
        menu.addMenuOption(nextUrl + DAY.name(), getMessage(optionKey + "day", user));
        menu.addMenuOption(nextUrl + WEEK.name(), getMessage(optionKey + "week", user));
        menu.addMenuOption(voteMenus + "time_custom" + entityUidUrlSuffix + requestUid, getMessage(optionKey + "custom", user));
        return menu;
    }

    @RequestMapping(value = pathNew + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam String inputNumber,
                                    @RequestParam String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time_custom", requestUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey + "-custom", user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "confirm" + entityUidUrlSuffix + requestUid + "&field=custom");

        return menuBuilder(menu);

    }

    @RequestMapping(value = pathNew + "confirm")
    @ResponseBody
    public Request confirmVoteSend(@RequestParam String msisdn, @RequestParam String requestUid,
                                   @RequestParam(value = "request") String userInput,
                                   @RequestParam(required = false) String field,
                                   @RequestParam(required = false) VoteTime time,
                                   @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        String lastMenu = field == null ? "standard" : field;

        if (interrupted == null || !interrupted) {
            if ("standard".equals(lastMenu)) {
                setStandardTime(requestUid, time, user);
            } else if ("custom".equals(lastMenu)) {
                setCustomTime(requestUid, userInput, user);
            } else if ("issue".equals(lastMenu)) {
                adjustSubject(requestUid, userInput, user);
            }
        }

        EventRequest vote = eventRequestBroker.load(requestUid);
        String[] promptFields = new String[]{vote.getName(), "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat)};

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "confirm", promptKey, promptFields, user));
        menu.addMenuOption(menusNew + "send?requestUid=" + requestUid, getMessage(thisSection, "confirm", optionsKey + "yes", user));
        menu.addMenuOption(backVoteUrl("subject", requestUid), getMessage(thisSection, "confirm", optionsKey + "topic", user));
        menu.addMenuOption(backVoteUrl("closing", requestUid), getMessage(thisSection, "confirm", optionsKey + "time", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = pathNew + "send")
    @ResponseBody
    public Request voteSendDo(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, null);
        USSDMenu menu;

        try {
            String createdUid = eventRequestBroker.finish(user.getUid(), requestUid, true);
            Event vote = eventBroker.load(createdUid);
            int eventsLeft = accountGroupBroker.numberEventsLeftForParent(vote.getUid());
            final String prompt = eventsLeft < EVENT_LIMIT_WARNING_THRESHOLD ?
                    getMessage(thisSection, "send", promptKey + ".limit", String.valueOf(eventsLeft), user) :
                    getMessage(thisSection, "send", promptKey, user);
            menu = new USSDMenu(prompt, optionsHomeExit(user, false));
            return menuBuilder(menu);
        } catch (EventStartTimeNotInFutureException e) {
            final String messageKey = USSDSection.VOTES.toKey() + "send.err.past.";
            menu = new USSDMenu(getMessage(messageKey + promptKey, user));
            menu.setFreeText(false);
            menu.addMenuOption(voteMenus + "send-reset" + entityUidUrlSuffix + requestUid, getMessage(messageKey + "yes", user));
            menu.addMenuOption(backVoteUrl("time", requestUid), getMessage(messageKey + "no", user));
            return menuBuilder(menu);
        } catch (AccountLimitExceededException e) {
            return menuBuilder(eventUtil.outOfEventsMenu(thisSection, voteMenus + "new", optionsHomeExit(user, true), user));
        }
    }


    @RequestMapping(value = path + "send-reset")
    public Request voteSendResetTime(@RequestParam(value = phoneNumber) String inputNumber,
                                     @RequestParam String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        setStandardTime(requestUid, INSTANT, user);
        eventRequestBroker.finish(user.getUid(), requestUid, true);
        return menuBuilder(new USSDMenu(getMessage(thisSection, "send", promptKey, user), optionsHomeExit(user, false)));
    }


    private void setCustomTime(String requestUid, String userInput, User user) {
        LocalDateTime parsedTime = eventUtil.parseDateTime(userInput);
        userLogger.recordUserInputtedDateTime(user.getUid(), userInput, "vote-custom", UserInterfaceType.USSD);
        eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, parsedTime);
    }

    private void setStandardTime(String requestUid, VoteTime time, User user) {
        ZonedDateTime proposedDateTime = null;
        ZonedDateTime zonedNow = Instant.now().atZone(DateTimeUtil.getSAST());

        switch (time) {
            case INSTANT:
                proposedDateTime = zonedNow.plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS);
                break;
            case HOUR:
                proposedDateTime = zonedNow.plusHours(1L);
                break;
            case DAY:
                proposedDateTime = zonedNow.plusDays(1L);
                break;
            case WEEK:
                proposedDateTime = zonedNow.plusWeeks(1L);
                break;
            case CUSTOM:
                proposedDateTime = zonedNow.plusMinutes(7L);
                break;
        }

        eventRequestBroker.updateEventDateTime(user.getUid(), requestUid,
                proposedDateTime.toLocalDateTime());
    }

    private void adjustSubject(String requestUid, String userInput, User user) {
        eventRequestBroker.updateName(user.getUid(), requestUid, userInput);
        EventRequest vote = eventRequestBroker.load(requestUid);
        if (vote.getEventStartDateTime().isBefore(Instant.now().plus(7, ChronoUnit.MINUTES))) {
            // user is manipulating an "instant" vote so need to reset the counter, else may expire before send
            eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, LocalDateTime.now().plusMinutes(7L));
        }
    }



}