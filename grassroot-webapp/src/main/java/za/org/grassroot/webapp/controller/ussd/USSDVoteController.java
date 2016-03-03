package za.org.grassroot.webapp.controller.ussd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.webapp.util.USSDUrlUtil.backVoteUrl;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveVoteMenu;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController extends USSDController {

    private static final Logger log = LoggerFactory.getLogger(USSDVoteController.class);

    @Autowired
    private USSDEventUtil eventUtil;

    private static final String path = homePath + voteMenus;
    private static final USSDSection thisSection = USSDSection.VOTES;

    // for stubbing with Mockito
    public void setEventUtil(USSDEventUtil eventUtil) {
        this.eventUtil = eventUtil;
    }

    /*
    First menu asks user to select a group. Until we have a "snap voting" functionality worked out, this requires
    the user to have a group already set up (i.e., is different from meeting menu, which allows within-flow group creation
    Major todo: add menus to see status of vote while in progress, and possibly trigger reminder
     */
    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request votingStart(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        int hasVotesToView = eventManager.userHasEventsToView(user, EventType.Vote);
        log.info("Checked for votes to view ... got integer: " + hasVotesToView);
        USSDMenu menu;

        if (hasVotesToView >= -1) {
            menu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
            menu.addMenuOption(voteMenus + "new", getMessage(thisSection, startMenu, optionsKey + "new", user));
            if (hasVotesToView >= 0)
                menu.addMenuOption(voteMenus + "open", getMessage(thisSection, startMenu, optionsKey + "open", user));
            if (hasVotesToView <= 0)
                menu.addMenuOption(voteMenus + "old", getMessage(thisSection, startMenu, optionsKey + "old", user));
        } else {
            String groupsExistPrompt = getMessage(thisSection, "group", promptKey, user);
            String groupsDontExistPrompt = getMessage(thisSection, "group", promptKey + "-nogroup", user);
            menu = ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, groupsExistPrompt, groupsDontExistPrompt,
                    "issue", groupMenus + "create", null, false);
        }

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "new")
    @ResponseBody
    public Request newVote(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, voteMenus + "new");
        String groupsExistPrompt = getMessage(thisSection, "group", promptKey, user);
        String groupsDontExistPrompt = getMessage(thisSection, "group", promptKey + "-nogroup", user);
        return menuBuilder(ussdGroupUtil.askForGroupNoInlineNew(user, thisSection, groupsExistPrompt, groupsDontExistPrompt,
                "issue", groupMenus + "create", null, false));
    }

    /*
    Second menu asks the user to enter the issue that will be voted upon
    todo: some form of length restriction / checking
     */
    @RequestMapping(value = path + "issue")
    @ResponseBody
    public Request votingIssue(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupIdParam, required = false) Long groupId,
                               @RequestParam(value = eventIdParam, required = false) Long eventId,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                               @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user;

        if (interrupted || revising) {
            user = userManager.findByInputNumber(inputNumber, saveVoteMenu("issue", 1L));
        } else {
            user = userManager.findByInputNumber(inputNumber);
            eventId = eventManager.createVote(user, groupId).getId();
            userManager.setLastUssdMenu(user, saveVoteMenu("issue", eventId));
        }

        String nextUrl = (!revising) ? voteMenus + "time" + eventIdUrlSuffix + eventId :
                voteMenus + "confirm" + eventIdUrlSuffix + eventId + "&field=issue";

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "issue", promptKey, user), nextUrl);
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
                              @RequestParam(value = userInputParam) String issue,
                              @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                              @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time", eventId));

        if (!interrupted && !revising) eventManager.setSubject(eventId, issue);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey, user));

        String nextUrl = voteMenus + "confirm" + eventIdUrlSuffix + eventId + "&field=standard&time=";
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
                                    @RequestParam(value = eventIdParam) Long eventId,
                                    @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time_custom", eventId));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey + "-custom", user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "confirm" + eventIdUrlSuffix + eventId + "&field=custom");

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
                               @RequestParam(value = "field", required = false) String field,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("confirm", eventId));

        String[] promptFields;

        if (!interrupted) {
            switch (field) {
                case "standard":
                    promptFields = setStandardTime(eventId, time, user);
                    break;
                case "custom":
                    promptFields = setCustomTime(eventId, userInput);
                    break;
                case "issue":
                    promptFields = adjustSubject(eventId, userInput, user);
                    break;
                default:
                    promptFields = new String[]{"Error!", "Error occurred!"};
                    break;
            }
        } else {
            Event vote = eventManager.loadEvent(eventId);
            promptFields = new String[]{vote.getName(), "at " + vote.getEventStartDateTime().toLocalDateTime().format(dateTimeFormat)};
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "confirm", promptKey, promptFields, user));
        menu.addMenuOption(voteMenus + "send" + eventIdUrlSuffix + eventId, getMessage(thisSection, "confirm", optionsKey + "yes", user));
        menu.addMenuOption(backVoteUrl("issue", eventId), getMessage(thisSection, "confirm", optionsKey + "topic", user));
        menu.addMenuOption(backVoteUrl("time", eventId), getMessage(thisSection, "confirm", optionsKey + "time", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "open")
    @ResponseBody
    public Request viewOpenVotes(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        // todo: consider doing a save and return
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "open");
        String prompt = getMessage(thisSection, "open", promptKey, user);
        return menuBuilder(eventUtil.listUpcomingEvents(user, thisSection, prompt, "details?back=open"));
    }

    @RequestMapping(value = path + "old")
    @ResponseBody
    public Request viewOldVotes(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "old");
        String prompt = getMessage(thisSection, "old", promptKey, user);
        return menuBuilder(eventUtil.listPriorEvents(user, thisSection, prompt, voteMenus + "details?back=old", true));
    }

    @RequestMapping(value = path + "details")
    @ResponseBody
    public Request viewVoteDetails(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = eventIdParam) Long eventId,
                                   @RequestParam(value = "back") String backMenu) throws URISyntaxException {
        // todo: decide whether to allow users to change the closing time (& permissions, of course)
        // todo: have some way of counting reminders and only allow once unless paid account
        // todo: reconsider whether to save URL here, might want to set back to null

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("details", eventId));
        Event vote = eventManager.loadEvent(eventId);
        boolean futureEvent = vote.getEventStartDateTime().toLocalDateTime().isAfter(LocalDateTime.now());

        RSVPTotalsDTO voteResults = eventManager.getVoteResultsDTO(vote);
        String[] fields = new String[]{vote.getAppliesToGroup().getName(""), vote.getName(), "" + voteResults.getYes(),
                "" + voteResults.getNo(), "" + voteResults.getMaybe(), "" + voteResults.getNumberNoRSVP()};
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "details", promptKey, fields, user));

        menu.addMenuOption(voteMenus + backMenu, getMessage(thisSection, "details", optionsKey + "back", user));
        if (futureEvent)
            menu.addMenuOption("reminder" + eventIdUrlSuffix + eventId, getMessage(thisSection, "details", optionsKey + "reminder", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "reminder")
    @ResponseBody
    public Request sendVoteReminderConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = eventIdParam) Long eventId) throws URISyntaxException {

        // todo: mention how many people will get the reminder
        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("reminder", eventId));
        Event vote = eventManager.loadEvent(eventId);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "reminder", promptKey, user));
        menu.addMenuOptions(optionsYesNo(user, voteMenus + "reminder-do?eventId=" + eventId,
                voteMenus + "details?eventId=" + eventId));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "reminder-do")
    @ResponseBody
    public Request sendVoteReminderDo(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = eventIdParam) Long eventId) throws URISyntaxException {

        // use meeting reminder functions
        User user = userManager.findByInputNumber(inputNumber, null);
        eventManager.sendManualReminder(eventManager.loadEvent(eventId), "");
        return menuBuilder(new USSDMenu(getMessage(thisSection, "reminder-do", promptKey, user), optionsHomeExit(user)));

    }


    private String[] setCustomTime(Long eventId, String userInput) {
        Event vote = eventUtil.updateEventAndBlockSend(eventId, "time", userInput);
        final String dateTimePrompt = "at " + DateTimeUtil.parseDateTime(userInput).format(dateTimeFormat);
        return new String[]{vote.getName(), dateTimePrompt};
    }


    private String[] setStandardTime(Long eventId, String time, User user) {

        final LocalDateTime proposedDateTime;
        final String dateTimePrompt = getMessage(thisSection, "confirm", "time." + time, user);

        switch (time) {
            case "instant":
                proposedDateTime = LocalDateTime.now().plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS);
                break;
            case "hour":
                proposedDateTime = LocalDateTime.now().plusHours(1L);
                break;
            case "day":
                proposedDateTime = LocalDateTime.now().plusDays(1L);
                break;
            case "week":
                proposedDateTime = LocalDateTime.now().plusWeeks(1L);
                break;
            default:
                // this should never be called, but need it else Java throws error -- defaulting to instant
                proposedDateTime = LocalDateTime.now().plusMinutes(7L);
                break;
        }

        eventManager.setSendBlock(eventId);
        Event vote = eventManager.setEventTimestamp(eventId, Timestamp.valueOf(proposedDateTime));
        return new String[]{vote.getName(), dateTimePrompt};

    }

    private String[] adjustSubject(Long eventId, String userInput, User user) {
        String dateTime;
        Event vote = eventUtil.updateEventAndBlockSend(eventId, "subject", userInput);
        if (vote.getEventStartDateTime().toLocalDateTime().isBefore(LocalDateTime.now().plusMinutes(7L))) {
            // user is manipulating an "instant" vote so need to reset the counter, else may expire before send
            eventManager.setEventTimestamp(eventId, Timestamp.valueOf(LocalDateTime.now().plusMinutes(7L)));
            dateTime = getMessage(thisSection, "confirm", "time.instant", user);
        } else {
            // need a quick way to do "at" in i18n
            dateTime = "at " + vote.getEventStartDateTime().toLocalDateTime().format(dateTimeFormat);
        }
        return new String[]{userInput, dateTime};
    }

    /*
    Send out and confirm it has been sent
     */
    @RequestMapping(value = path + "send")
    @ResponseBody
    public Request voteSend(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam(value = eventIdParam) Long eventId) throws Exception {

        User user;
        try {
            user = userManager.findByInputNumber(inputNumber, null);
        } catch (NoSuchUserException e) {
            return noUserError;
        }

        Event vote = eventManager.removeSendBlock(eventId);
        log.info("Vote details confirmed! Closing date and time: " + vote.getEventStartDateTime().toLocalDateTime().format(dateTimeFormat));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "send", promptKey, user), optionsHomeExit(user));

        return menuBuilder(menu);

    }

}