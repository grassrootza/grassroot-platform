package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.NoSuchUserException;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.model.ussd.AAT.Request;

import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDVoteController.class);

    private static final String subMenuPath = USSD_BASE + VOTE_MENUS;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM, h:mm a");

    /*
    First menu asks user to select a group. Until we have a "snap voting" functionality worked out, this requires
    the user to have a group already set up (i.e., is different from meeting menu, which allows within-flow group creation
    Major todo: add menus to see status of vote while in progress, and possibly trigger reminder
    Major todo: add an option to do an "instant vote", i.e., call a vote on a temporary group
     */
    @RequestMapping(value = subMenuPath + START_KEY)
    @ResponseBody
    public Request votingStart(@RequestParam(value = PHONE_PARAM) String inputNumber) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        USSDMenu menu = new USSDMenu();

        if (groupManager.canUserCallVoteOnAnyGroup(user)) {
            // todo: restrict to groups on which user can call votes (via permissions)
            menu = userGroupMenu(user, "Okay, which group will be voting?", VOTE_MENUS + "issue", false);
        } else {
            menu.setPromptMessage("Sorry, you need to create a group first before calling a vote");
            menu.addMenuOption(GROUP_MENUS + "create", "Create a group");
            menu.addMenuOption(START_KEY, "Back to start");
        }

        return menuBuilder(menu);

    }

    /*
    Second menu asks the user to enter the issue that will be voted upon
    todo: some form of length restriction / checking
     */
    @RequestMapping(value = subMenuPath + "issue")
    @ResponseBody
    public Request votingIssue(@RequestParam(value = PHONE_PARAM) String inputNumber,
                               @RequestParam(value = GROUP_PARAM) Long groupId) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        Event vote = eventManager.createVote(user, groupId);

        USSDMenu menu = new USSDMenu("What is the subject of the vote?");
        menu.setFreeText(true);
        menu.setNextURI(VOTE_MENUS + "time" + EVENTID_URL + vote.getId());

        return menuBuilder(menu);

    }

    /*
    Third menu asks the user when the vote will close. Options are "instant vote", i.e., 5 minutes, versus "one day",
    versus "custom".
     */
    @RequestMapping(value = subMenuPath + "time")
    @ResponseBody
    public Request votingTime(@RequestParam(value = PHONE_PARAM) String inputNumber,
                              @RequestParam(value = EVENT_PARAM) Long eventId,
                              @RequestParam(value = TEXT_PARAM) String issue) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        Event vote = eventManager.setSubject(eventId, issue);

        USSDMenu menu = new USSDMenu("When will the vote close?");

        String nextUrl = VOTE_MENUS + "confirm" + EVENTID_URL + eventId + "&time=";

        menu.addMenuOption(nextUrl + "instant", "In five minutes (instant poll)");
        menu.addMenuOption(nextUrl + "hour", "In an hour");
        menu.addMenuOption(nextUrl + "day", "In one day");
        menu.addMenuOption(nextUrl + "week", "In one week");
        menu.addMenuOption(VOTE_MENUS + "time_custom" + EVENTID_URL + eventId, "Custom");

        return menuBuilder(menu);
    }

    /*
    Optional menu if user wants to enter a custom expiry time
     */
    @RequestMapping(value = subMenuPath + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam(value = PHONE_PARAM) String inputNumber,
                                    @RequestParam(value = EVENT_PARAM) Long eventId) {
        return null;
    }

    /*
    Final menu asks for confirmation, then sends out
     */
    @RequestMapping(value = subMenuPath + "confirm")
    @ResponseBody
    public Request voteConfirm(@RequestParam(value = PHONE_PARAM) String inputNumber,
                               @RequestParam(value = EVENT_PARAM) Long eventId,
                               @RequestParam(value = "time") String time,
                               @RequestParam(value = "custom", required = false) boolean custom) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        Event vote = eventManager.loadEvent(eventId);

        final Date proposedDateTime;
        final String dateTimePrompt;
        final String dateTimeFormatted;

        if (!custom) {
            switch (time) {
                case "instant":
                    proposedDateTime = DateTimeUtil.addMinutesToDate(new Date(), 5);
                    dateTimeFormatted = dateFormat.format(proposedDateTime);
                    dateTimePrompt = " in five minutes";
                    break;
                case "hour":
                    proposedDateTime = DateTimeUtil.addHoursToDate(new Date(), 1);
                    dateTimeFormatted = dateFormat.format(proposedDateTime);
                    dateTimePrompt = " in one hour";
                    break;
                case "day":
                    proposedDateTime = DateTimeUtil.addHoursToDate(new Date(), 24);
                    dateTimeFormatted = dateFormat.format(proposedDateTime);
                    dateTimePrompt = " at " + dateTimeFormatted;
                    break;
                case "week":
                    proposedDateTime = DateTimeUtil.addHoursToDate(new Date(), 7*24);
                    dateTimeFormatted = dateFormat.format(proposedDateTime);
                    dateTimePrompt = " at " + dateTimeFormatted;
                    break;
                default:
                    // this should never be called, but need it else Java throws error -- defaulting to instant
                    proposedDateTime = DateTimeUtil.addMinutesToDate(new Date(), 5);
                    dateTimeFormatted = dateFormat.format(proposedDateTime);
                    dateTimePrompt = " in five minutes";
            }
        } else {
            // todo: use the same Date objects for all of these (should switch all to Java 8 LocalDateTime, eventually)
            LocalDateTime parsedDate = DateTimeUtil.parseDateTime(time);
            proposedDateTime = Date.from(parsedDate.atZone(ZoneId.systemDefault()).toInstant());

            // dateTimeFormatted = parsedDate.format(DateTimeFormatter.ofPattern(dateFormat));
            dateTimeFormatted = dateFormat.format(proposedDateTime);
            dateTimePrompt = " at " + dateTimeFormatted;
        }

        final String dateTimeParam = encodeParamater(dateTimeFormatted);
        final String confirmPrompt = "You are calling a vote about \'" + vote.getName() + "\', which will close" + dateTimePrompt
                + ". Correct?";

        USSDMenu menu = new USSDMenu(confirmPrompt);
        menu.addMenuOption(MTG_MENUS + "send" + EVENTID_URL + eventId + "&time=" + dateTimeParam, "Yes, send out");
        menu.addMenuOption(MTG_MENUS + START_KEY, "No, start again");

        return menuBuilder(menu);
    }

    /*
    Send out and confirm it has been sent
     */
    @RequestMapping(value = subMenuPath + "send")
    @ResponseBody
    public Request voteSend(@RequestParam(value = PHONE_PARAM) String inputNumber,
                            @RequestParam(value = EVENT_PARAM) Long eventId,
                            @RequestParam(value = "time") String confirmedTime) throws Exception {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        Event vote = eventManager.setEventTimestamp(eventId, new Timestamp(dateFormat.parse(confirmedTime).getTime()));
        USSDMenu menu = new USSDMenu("Vote sent out!", optionsHomeExit(user));

        return menuBuilder(menu);

    }

}
