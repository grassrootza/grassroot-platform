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
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDVoteController.class);

    private static final String path = homePath + voteMenus;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE d MMM, h:mm a");
    private static final SimpleDateFormat dateWithYear = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    /*
    First menu asks user to select a group. Until we have a "snap voting" functionality worked out, this requires
    the user to have a group already set up (i.e., is different from meeting menu, which allows within-flow group creation
    Major todo: add menus to see status of vote while in progress, and possibly trigger reminder
    Major todo: add an option to do an "instant vote", i.e., call a vote on a temporary group
     */
    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request votingStart(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        USSDMenu menu = new USSDMenu();

        if (groupManager.canUserCallVoteOnAnyGroup(user)) {
            // todo: restrict to groups on which user can call votes (via permissions)
            menu = ussdGroupUtil.userGroupMenuPageOne(user, getMessage(voteKey, startMenu, promptKey, user), voteMenus + "issue", null);
        } else {
            menu.setPromptMessage(getMessage(voteKey, startMenu, promptKey + "-nogroup", user));
            menu.addMenuOption(groupMenus + "create", getMessage(voteKey, startMenu, optionsKey + "create", user));
            menu.addMenuOption(startMenu, getMessage(voteKey, startMenu, optionsKey + "home", user));
        }

        return menuBuilder(menu);

    }

    /*
    Second menu asks the user to enter the issue that will be voted upon
    todo: some form of length restriction / checking
     */
    @RequestMapping(value = path + "issue")
    @ResponseBody
    public Request votingIssue(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupIdParam) Long groupId) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        Event vote = eventManager.createVote(user, groupId);

        USSDMenu menu = new USSDMenu(getMessage(voteKey, "issue", promptKey, user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "time" + eventIdUrlSuffix + vote.getId());

        return menuBuilder(menu);

    }

    /*
    Third menu asks the user when the vote will close. Options are "instant vote", i.e., 5 minutes, versus "one day",
    versus "custom".
     */
    @RequestMapping(value = path + "time")
    @ResponseBody
    public Request votingTime(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = eventIdParam) Long eventId,
                              @RequestParam(value = userInputParam) String issue) throws URISyntaxException {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        Event vote = eventManager.setSubject(eventId, issue);

        USSDMenu menu = new USSDMenu(getMessage(voteKey, "time", promptKey, user));

        String nextUrl = voteMenus + "confirm" + eventIdUrlSuffix + eventId + "&time=";
        String optionKey = voteKey + ".time." + optionsKey;

        menu.addMenuOption(nextUrl + "instant", getMessage(optionKey + "instant", user));
        menu.addMenuOption(nextUrl + "hour", getMessage(optionKey + "hour", user));
        menu.addMenuOption(nextUrl + "day", getMessage(optionKey + "day", user));
        menu.addMenuOption(nextUrl + "week", getMessage(optionKey + "week", user));
        menu.addMenuOption(voteMenus + "time_custom" + eventIdUrlSuffix + eventId, getMessage(optionKey + "custom", user));

        return menuBuilder(menu);
    }

    /*
    Optional menu if user wants to enter a custom expiry time
     */
    @RequestMapping(value = path + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = eventIdParam) Long eventId) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = new USSDMenu(getMessage(voteKey, "time", promptKey + "-custom", user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "confirm" + eventIdUrlSuffix + eventId + "&custom=true");

        return menuBuilder(menu);

    }

    /*
    Final menu asks for confirmation, then sends out
    major todo: shift the time strings into messages (and generally do i18n for this)
     */
    @RequestMapping(value = path + "confirm")
    @ResponseBody
    public Request voteConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = eventIdParam) Long eventId,
                               @RequestParam(value = userInputParam) String userInput,
                               @RequestParam(value = "time", required = false) String time,
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
                    proposedDateTime = DateTimeUtil.addMinutesToDate(new Date(), 7);
                    dateTimeFormatted = dateWithYear.format(proposedDateTime);
                    dateTimePrompt = " in five minutes";
                    break;
                case "hour":
                    proposedDateTime = DateTimeUtil.addHoursToDate(new Date(), 1);
                    dateTimeFormatted = dateWithYear.format(proposedDateTime);
                    dateTimePrompt = " in one hour";
                    break;
                case "day":
                    proposedDateTime = DateTimeUtil.addHoursToDate(new Date(), 24);
                    dateTimeFormatted = dateWithYear.format(proposedDateTime);
                    dateTimePrompt = " at " + dateTimeFormatted;
                    break;
                case "week":
                    proposedDateTime = DateTimeUtil.addHoursToDate(new Date(), 7*24);
                    dateTimeFormatted = dateWithYear.format(proposedDateTime);
                    dateTimePrompt = " at " + dateTimeFormatted;
                    break;
                default:
                    // this should never be called, but need it else Java throws error -- defaulting to instant
                    proposedDateTime = DateTimeUtil.addMinutesToDate(new Date(), 7);
                    dateTimeFormatted = dateWithYear.format(proposedDateTime);
                    dateTimePrompt = " in five minutes";
            }
        } else {
            // todo: use the same Date objects for all of these (should switch all to Java 8 LocalDateTime, eventually)
            LocalDateTime parsedDate = DateTimeUtil.parseDateTime(userInput);
            proposedDateTime = Date.from(parsedDate.atZone(ZoneId.systemDefault()).toInstant());

            // dateTimeFormatted = parsedDate.format(DateTimeFormatter.ofPattern(dateFormat));
            dateTimeFormatted = dateWithYear.format(proposedDateTime);
            dateTimePrompt = " at " + dateFormat.format(proposedDateTime);
        }

        final String dateTimeParam = USSDUrlUtil.encodeParameter(dateTimeFormatted);
        final String confirmPrompt = "You are calling a vote about \'" + vote.getName() + "\', which will close" + dateTimePrompt
                + ". Correct?";

        USSDMenu menu = new USSDMenu(confirmPrompt);
        menu.addMenuOption(voteMenus + "send" + eventIdUrlSuffix + eventId + "&time=" + dateTimeParam, "Yes, send out");
        menu.addMenuOption(voteMenus + startMenu, "No, start again");

        return menuBuilder(menu);
    }

    /*
    Send out and confirm it has been sent
     */
    @RequestMapping(value = path + "send")
    @ResponseBody
    public Request voteSend(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam(value = eventIdParam) Long eventId,
                            @RequestParam(value = "time") String confirmedTime) throws Exception {

        User user;
        try { user = userManager.findByInputNumber(inputNumber); }
        catch (NoSuchUserException e) { return noUserError; }

        log.info("Vote details confirmed! Closing date and time: " + confirmedTime);

        Event vote = eventManager.setEventTimestamp(eventId, new Timestamp(dateWithYear.parse(confirmedTime).getTime()));
        USSDMenu menu = new USSDMenu("Vote sent out!", optionsHomeExit(user));

        return menuBuilder(menu);

    }

}