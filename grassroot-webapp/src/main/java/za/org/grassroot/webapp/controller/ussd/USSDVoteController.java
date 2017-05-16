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
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.enums.EventListTimeType;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.EventRequestBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

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

    @Value("${grassroot.events.limit.enabled:false}")
    private boolean eventMonthlyLimitActive;

    private static final int EVENT_LIMIT_WARNING_THRESHOLD = 5; // only warn when below this

    private final EventRequestBroker eventRequestBroker;
    private final EventLogBroker eventLogBroker;
    private final PermissionBroker permissionBroker;
    private final AccountGroupBroker accountGroupBroker;

    private USSDEventUtil eventUtil;

    private static final String path = homePath + voteMenus;
    private static final USSDSection thisSection = USSDSection.VOTES;

    @Autowired
    public USSDVoteController(EventRequestBroker eventRequestBroker, EventLogBroker eventLogBroker, EventLogRepository eventLogRepository, PermissionBroker permissionBroker, AccountGroupBroker accountGroupBroker) {
        this.eventRequestBroker = eventRequestBroker;
        this.eventLogBroker = eventLogBroker;
        this.permissionBroker = permissionBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @Autowired
    public void setEventUtil(USSDEventUtil eventUtil) {
        this.eventUtil = eventUtil;
    }

    /*
    First menu asks user to select a group. Until we have a "snap voting" functionality worked out, this requires
    the user to have a group already set up (i.e., is different from meeting menu, which allows within-flow group creation
     */
    @RequestMapping(value = path + startMenu)
    @ResponseBody
    public Request votingStart(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        EventListTimeType hasVotesToView = eventBroker.userHasEventsToView(user, EventType.VOTE);
        log.info("Checked for votes to view ... got integer: " + hasVotesToView);
        USSDMenu menu;

        if (!hasVotesToView.equals(EventListTimeType.NONE)) {
            menu = new USSDMenu(getMessage(thisSection, startMenu, promptKey, user));
            menu.addMenuOption(voteMenus + "new", getMessage(thisSection, startMenu, optionsKey + "new", user));
            if (!hasVotesToView.equals(EventListTimeType.PAST)) // ie is either both or future
                menu.addMenuOption(voteMenus + "open", getMessage(thisSection, startMenu, optionsKey + "open", user));
            if (!hasVotesToView.equals(EventListTimeType.FUTURE)) // ie is either both or past
                menu.addMenuOption(voteMenus + "old", getMessage(thisSection, startMenu, optionsKey + "old", user));
            menu.addMenuOption("start", getMessage(USSDSection.VOTES,"start","options.back",user));
        } else {
            menu = initiateNewVote(user);
        }

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "new")
    @ResponseBody
    public Request newVote(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "new");
        USSDMenu menu = initiateNewVote(user);
        return menuBuilder(menu);
    }

    private USSDMenu initiateNewVote(User user) throws URISyntaxException {
        int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        if (possibleGroups == 1) {
            Group group = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE).iterator().next();
            final String prompt = getMessage(thisSection, "issue", promptKey + ".skipped", group.getName(""), user);
            return setVoteGroupAndInitiateRequest(prompt, null, group.getUid(), "time", "", user);
        } else {
            return ussdGroupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection)
                    .urlForExistingGroup("issue").numberOfGroups(possibleGroups));
        }
    }

    /*
    Second menu asks the user to enter the issue that will be voted upon
     */
    @RequestMapping(value = path + "issue")
    @ResponseBody
    public Request votingIssue(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = groupUidParam, required = false) String groupUid,
                               @RequestParam(value = entityUidParam, required = false) String requestUid,
                               @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber);
        USSDMenu menu = setVoteGroupAndInitiateRequest(getMessage(thisSection, "issue", promptKey, user),
                requestUid, groupUid, revising ? "confirm" : "time", revising ? "&field=issue" : "", user);
        return menuBuilder(menu);
    }

    private USSDMenu setVoteGroupAndInitiateRequest(String menuPrompt, String interruptedRequestUid, String groupUid,
                                                    String subsequentMenu, String paramsToPassForward, User user) {
        String requestUid;
        if (StringUtils.isEmpty(interruptedRequestUid)) {
            VoteRequest voteRequest = eventRequestBroker.createEmptyVoteRequest(user.getUid(), groupUid);
            requestUid = voteRequest.getUid();
        } else {
            requestUid = interruptedRequestUid;
            VoteRequest savedRequest = (VoteRequest) eventRequestBroker.load(requestUid);
            groupUid = savedRequest.getParent().getUid();
        }
        cacheManager.putUssdMenuForUser(user.getPhoneNumber(), saveVoteMenu("issue", requestUid));

        int eventsLeft = accountGroupBroker.numberEventsLeftForGroup(groupUid);
        if (eventMonthlyLimitActive && eventsLeft == 0) {
            return eventUtil.outOfEventsMenu(thisSection, voteMenus + "new", optionsHomeExit(user, true), user);
        } else {
            final String prompt = eventsLeft >= EVENT_LIMIT_WARNING_THRESHOLD ? menuPrompt
                    : getMessage(thisSection, "issue", promptKey + ".limit", String.valueOf(eventsLeft), user);
            return new USSDMenu(prompt, voteMenus + subsequentMenu + entityUidUrlSuffix + requestUid + paramsToPassForward);
        }
    }

    /*
    Third menu asks the user when the vote will close. Options are "instant vote", i.e., 5 minutes, versus "one day",
    versus "custom".
     */
    @RequestMapping(value = path + "time")
    @ResponseBody
    public Request votingTime(@RequestParam(value = phoneNumber) String inputNumber,
                              @RequestParam(value = entityUidParam) String requestUid,
                              @RequestParam(value = userInputParam) String issue,
                              @RequestParam(value = interruptedFlag, required = false) boolean interrupted,
                              @RequestParam(value = revisingFlag, required = false) boolean revising) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time", requestUid));

        if (!interrupted && !revising) eventRequestBroker.updateName(user.getUid(), requestUid, issue);

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey, user));

        String nextUrl = voteMenus + "confirm" + entityUidUrlSuffix + requestUid + "&field=standard&time=";
        String optionKey = voteKey + ".time." + optionsKey;

        menu.addMenuOption(nextUrl + "instant", getMessage(optionKey + "instant", user));
        menu.addMenuOption(nextUrl + "hour", getMessage(optionKey + "hour", user));
        menu.addMenuOption(nextUrl + "day", getMessage(optionKey + "day", user));
        menu.addMenuOption(nextUrl + "week", getMessage(optionKey + "week", user));
        menu.addMenuOption(voteMenus + "time_custom" + entityUidUrlSuffix + requestUid, getMessage(optionKey + "custom", user));

        return menuBuilder(menu);
    }

    /*
    Optional menu if user wants to enter a custom expiry time
     */
    @RequestMapping(value = path + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam(value = phoneNumber) String inputNumber,
                                    @RequestParam(value = entityUidParam) String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("time_custom", requestUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey + "-custom", user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "confirm" + entityUidUrlSuffix + requestUid + "&field=custom");

        return menuBuilder(menu);

    }

    /*
    Final menu asks for confirmation, then sends out
     */
    @RequestMapping(value = path + "confirm")
    @ResponseBody
    public Request voteConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                               @RequestParam(value = entityUidParam) String requestUid,
                               @RequestParam(value = userInputParam) String userInput,
                               @RequestParam(value = "time", required = false) String time,
                               @RequestParam(value = "field", required = false) String field,
                               @RequestParam(value = interruptedFlag, required = false) boolean interrupted) throws URISyntaxException {

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("confirm", requestUid));

        String[] promptFields;

        if (!interrupted) {
            switch (field) {
                case "standard":
                    promptFields = setStandardTime(requestUid, time, user);
                    break;
                case "custom":
                    promptFields = setCustomTime(requestUid, userInput, user);
                    break;
                case "issue":
                    promptFields = adjustSubject(requestUid, userInput, user);
                    break;
                default:
                    promptFields = new String[]{"Error!", "Error occurred!"};
                    break;
            }
        } else {
            EventRequest vote = eventRequestBroker.load(requestUid);
            promptFields = new String[]{vote.getName(), "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat)};
        }

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "confirm", promptKey, promptFields, user));
        menu.addMenuOption(voteMenus + "send" + entityUidUrlSuffix + requestUid,
                           getMessage(thisSection, "confirm", optionsKey + "yes", user));
        menu.addMenuOption(backVoteUrl("issue", requestUid), getMessage(thisSection, "confirm", optionsKey + "topic", user));
        menu.addMenuOption(backVoteUrl("time", requestUid), getMessage(thisSection, "confirm", optionsKey + "time", user));

        return menuBuilder(menu);
    }

    /*
    Send out and confirm it has been sent
     */
    @RequestMapping(value = path + "send")
    @ResponseBody
    public Request voteSend(@RequestParam(value = phoneNumber) String inputNumber,
                            @RequestParam(value = entityUidParam) String requestUid) throws URISyntaxException {

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
                                     @RequestParam(value = entityUidParam) String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, null);
        setStandardTime(requestUid, "instant", user);
        eventRequestBroker.finish(user.getUid(), requestUid, true);
        return menuBuilder(new USSDMenu(getMessage(thisSection, "send", promptKey, user), optionsHomeExit(user, false)));
    }

    /**
     * SECTION: menus to view open & old votes
     */

    @RequestMapping(value = path + "open")
    @ResponseBody
    public Request viewOpenVotes(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "open");
        String prompt = getMessage(thisSection, "open", promptKey, user);
        return menuBuilder(eventUtil.listUpcomingEvents(user, thisSection, prompt, "details?back=open", false, null, null));
    }

    @RequestMapping(value = path + "old")
    @ResponseBody
    public Request viewOldVotes(@RequestParam(value = phoneNumber) String inputNumber) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber, voteMenus + "old");
        String prompt = getMessage(thisSection, "old", promptKey, user);
        return menuBuilder(eventUtil.listPriorEvents(user, thisSection, prompt, "details?back=old", true));
    }

    @RequestMapping(value = path + "details")
    @ResponseBody
    public Request viewVoteDetails(@RequestParam(value = phoneNumber) String inputNumber,
                                   @RequestParam(value = entityUidParam) String eventUid,
                                   @RequestParam(value = "back") String backMenu) throws URISyntaxException {
        // todo: decide whether to allow users to change the closing time (& permissions, of course)
        // todo: have some way of counting reminders and only allow once unless paid account
        // todo: reconsider whether to save URL here, might want to set back to null

        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("details", eventUid) + "&back=" + backMenu  );
        Event vote = eventBroker.load(eventUid);
        boolean futureEvent = vote.getEventStartDateTime().isAfter(Instant.now());
        ResponseTotalsDTO voteResults = eventLogBroker.getResponseCountForEvent(vote);

        USSDMenu menu;

        if (futureEvent) {
            EventLog userResponse = eventLogBroker.fetchResponseForEvent(vote, user);

            String responseText;
            List<String> otherResponses;
            if  (userResponse == null || !userResponse.hasValidResponse()) {
                responseText = "not voted yet";
                otherResponses = Arrays.asList("yes", "no", "abstain");
            } else {
                switch (userResponse.getResponse()) {
                    case YES:
                        responseText = "yes";
                        otherResponses = Arrays.asList("no", "abstain");
                        break;
                    case NO:
                        responseText = "no";
                        otherResponses = Arrays.asList("yes", "abstain");
                        break;
                    case MAYBE:
                        responseText = "abstain";
                        otherResponses = Arrays.asList("yes", "no");
                        break;
                    default:
                        responseText = "not voted yet";
                        otherResponses = Arrays.asList("yes", "no", "abstain");
                        break;
                }
            }

            final String suffix = entityUidUrlSuffix + eventUid;
            final String[] fields = new String[] { vote.getAncestorGroup().getName(""), vote.getName(),
                    "" + (voteResults.getNumberOfUsers() - voteResults.getNumberNoRSVP()), responseText };

            menu = new USSDMenu(getMessage(thisSection, "details", "future." + promptKey, fields, user));
            for (String voteOption : otherResponses) {
                menu.addMenuOption(voteMenus + "change-vote" + suffix + "&response=" + voteOption,
                                   getMessage(thisSection, "details", optionsKey + "change", voteOption, user));
            }

        } else {
            String[] fields = new String[]{vote.getAncestorGroup().getName(""), vote.getName(), "" + voteResults.getYes(),
                    "" + voteResults.getNo(), "" + voteResults.getMaybe(), "" + voteResults.getNumberNoRSVP()};
            menu = new USSDMenu(getMessage(thisSection, "details", promptKey, fields, user));
        }

        menu.addMenuOption(voteMenus + backMenu, getMessage(thisSection, "details", optionsKey + "back", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "change-vote")
    @ResponseBody
    public Request changeVoteDo(@RequestParam(value = phoneNumber) String inputNumber,
                                @RequestParam(value = entityUidParam) String eventUid,
                                @RequestParam(value = "response") String response) throws  URISyntaxException {

        final User user = userManager.findByInputNumber(inputNumber, null);
        final Event vote = eventBroker.load(eventUid);

        USSDMenu menu;
        if (vote.getEventStartDateTime().isBefore(Instant.now())) {
            menu = new USSDMenu(getMessage(thisSection, "change", "error", user));
        } else {
            // todo: replace this hack once responses are handled better
            EventRSVPResponse voteResponse = "abstain".equals(response) ? EventRSVPResponse.MAYBE :
                    EventRSVPResponse.fromString(response);
            eventLogBroker.rsvpForEvent(vote.getUid(), user.getUid(), voteResponse);
            menu = new USSDMenu(getMessage(thisSection, "change", "done", response, user));
        }

        menu.addMenuOption(voteMenus + "details" + entityUidUrlSuffix + eventUid + "&back=open",
                           getMessage(thisSection, "change", optionsKey + "back", user));
        menu.addMenuOptions(optionsHomeExit(user, false));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "reminder")
    @ResponseBody
    public Request sendVoteReminderConfirm(@RequestParam(value = phoneNumber) String inputNumber,
                                           @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {

        // todo: mention how many people will get the reminder
        User user = userManager.findByInputNumber(inputNumber, saveVoteMenu("reminder", eventUid));

        USSDMenu menu = new USSDMenu(getMessage(thisSection, "reminder", promptKey, user));
        menu.addMenuOptions(optionsYesNo(user, voteMenus + "reminder-do?eventId=" + eventUid,
                voteMenus + "details?eventId=" + eventUid));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "reminder-do")
    @ResponseBody
    public Request sendVoteReminderDo(@RequestParam(value = phoneNumber) String inputNumber,
                                      @RequestParam(value = entityUidParam) String eventUid) throws URISyntaxException {
        // use meeting reminder functions
        User user = userManager.findByInputNumber(inputNumber, null);
        eventBroker.sendManualReminder(user.getUid(), eventUid);
        return menuBuilder(new USSDMenu(getMessage(thisSection, "reminder-do", promptKey, user), optionsHomeExit(user, false)));
    }

    private String[] setCustomTime(String requestUid, String userInput, User user) {
        LocalDateTime parsedTime = eventUtil.parseDateTime(userInput);
        userLogger.recordUserInputtedDateTime(user.getUid(), userInput, "vote-custom", UserInterfaceType.USSD);
        eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, parsedTime);
        final String dateTimePrompt = "at " + parsedTime.format(dateTimeFormat);
        return new String[]{eventRequestBroker.load(requestUid).getName(), dateTimePrompt};
    }


    private String[] setStandardTime(String requestUid, String time, User user) {

        final ZonedDateTime proposedDateTime;
        final String dateTimePrompt = getMessage(thisSection, "confirm", "time." + time, user);

        ZonedDateTime zonedNow = Instant.now().atZone(DateTimeUtil.getSAST());

        switch (time) {
            case "instant":
                proposedDateTime = zonedNow.plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS);
                break;
            case "hour":
                proposedDateTime = zonedNow.plusHours(1L);
                break;
            case "day":
                proposedDateTime = zonedNow.plusDays(1L);
                break;
            case "week":
                proposedDateTime = zonedNow.plusWeeks(1L);
                break;
            default:
                // this should never be called, but need it else Java throws error -- defaulting to instant
                proposedDateTime = zonedNow.plusMinutes(7L);
                break;
        }

        eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, proposedDateTime.toLocalDateTime());
        EventRequest voteRequest = eventRequestBroker.load(requestUid);
        return new String[]{voteRequest.getName(), dateTimePrompt};

    }

    private String[] adjustSubject(String requestUid, String userInput, User user) {
        String dateTime;
        eventRequestBroker.updateName(user.getUid(), requestUid, userInput);
        EventRequest vote = eventRequestBroker.load(requestUid);
        if (vote.getEventStartDateTime().isBefore(Instant.now().plus(7, ChronoUnit.MINUTES))) {
            // user is manipulating an "instant" vote so need to reset the counter, else may expire before send
            eventRequestBroker.updateEventDateTime(user.getUid(), requestUid, LocalDateTime.now().plusMinutes(7L));
            dateTime = getMessage(thisSection, "confirm", "time.instant", user);
        } else {
            // need a quick way to do "at" in i18n
            dateTime = "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat);
        }
        return new String[]{userInput, dateTime};
    }



}