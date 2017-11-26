package za.org.grassroot.webapp.controller.ussd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventRequest;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventRequestBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.enums.VoteTime;
import za.org.grassroot.webapp.model.ussd.AAT.Request;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;
import static za.org.grassroot.webapp.enums.VoteTime.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/10/28.
 */
@RequestMapping(method = GET, produces = MediaType.APPLICATION_XML_VALUE)
@RestController
public class USSDVoteController extends USSDBaseController {

    @Value("${grassroot.events.limit.enabled:false}")
    private boolean eventMonthlyLimitActive;

    private static final int EVENT_LIMIT_WARNING_THRESHOLD = 5; // only warn when below this

    private final EventBroker eventBroker;
    private final EventRequestBroker eventRequestBroker;
    private final VoteBroker voteBroker;
    private final PermissionBroker permissionBroker;
    private final AccountGroupBroker accountGroupBroker;

    private USSDEventUtil eventUtil;
    private USSDGroupUtil groupUtil;

    private static final String path = homePath + voteMenus;
    private static final USSDSection thisSection = USSDSection.VOTES;

    @Autowired
    public USSDVoteController(EventBroker eventBroker, EventRequestBroker eventRequestBroker, VoteBroker voteBroker, PermissionBroker permissionBroker, AccountGroupBroker accountGroupBroker) {
        this.eventBroker = eventBroker;
        this.eventRequestBroker = eventRequestBroker;
        this.voteBroker = voteBroker;
        this.permissionBroker = permissionBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @Autowired
    public void setEventUtil(USSDEventUtil eventUtil) {
        this.eventUtil = eventUtil;
    }

    @Autowired
    protected void setGroupUtil(USSDGroupUtil groupUtil) {
        this.groupUtil = groupUtil;
    }

    private String menuUrl(String menu, String requestUid) {
        return voteMenus + menu + "?requestUid=" + requestUid;
    }

    /*
    Vote response menu
     */
    public USSDMenu assembleVoteMenu(User user, EntityForUserResponse entity) {
        Vote vote = (Vote) entity;

        final String[] promptFields = new String[]{vote.getAncestorGroup().getName(""),
                vote.getAncestorGroup().getMembership(vote.getCreatedByUser()).getDisplayName(),
                vote.getName()};

        final String voteUri = voteMenus + "record?voteUid=" + vote.getUid() + "&response=";
        final String optionMsgKey = voteKey + "." + optionsKey;

        USSDMenu openingMenu = new USSDMenu(getMessage(USSDSection.HOME, startMenu, promptKey + "-vote", promptFields, user));

        if (vote.getVoteOptions().isEmpty()) {
            openingMenu.addMenuOption(voteUri + "YES", getMessage(optionMsgKey + "yes", user));
            openingMenu.addMenuOption(voteUri + "NO", getMessage(optionMsgKey + "no", user));
            openingMenu.addMenuOption(voteUri + "ABSTAIN", getMessage(optionMsgKey + "abstain", user));
        } else {
            vote.getVoteOptions().forEach(o -> {
                openingMenu.addMenuOption(voteUri + USSDUrlUtil.encodeParameter(o), o);
            });
        }

        return openingMenu;
    }

    @RequestMapping(value = path + "record")
    @ResponseBody
    public Request voteAndWelcome(@RequestParam(value = phoneNumber) String inputNumber,
                                  @RequestParam String voteUid, @RequestParam String response) throws URISyntaxException {
        User user = userManager.findByInputNumber(inputNumber);
        voteBroker.recordUserVote(user.getUid(), voteUid, response);
        final String prompt = getMessage(thisSection, startMenu, promptKey + ".vote-recorded", user);
        cacheManager.clearRsvpCacheForUser(user, EventType.VOTE);
        return menuBuilder(new USSDMenu(prompt, optionsHomeExit(user, false)));
    }

    /*
    Restructured menus begin here: begin with subject
     */
    @RequestMapping(value = { path + startMenu, path + "subject" })
    @ResponseBody
    public Request voteSubject(@RequestParam String msisdn,
                               @RequestParam(required = false) String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, GROUP_PERMISSION_CREATE_GROUP_VOTE);
        // if request UID is not null then by definition we are here via confirmation return
        String nextUrl = StringUtils.isEmpty(requestUid) ? voteMenus + "type" : menuUrl("confirm", requestUid) + "&field=subject";
        if (!StringUtils.isEmpty(requestUid)) {
            cacheManager.putUssdMenuForUser(msisdn, saveVoteMenu("subject", requestUid));
        }
        // ask for group will by definition return the "no group" menu, since we are in this branch
        USSDMenu menu = possibleGroups != 0 ?
                new USSDMenu(getMessage(thisSection, "subject", promptKey, user), nextUrl) :
                groupUtil.askForGroup(new USSDGroupUtil.GroupMenuBuilder(user, thisSection));
        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "type")
    @ResponseBody
    public Request voteType(@RequestParam String msisdn, @RequestParam String request,
                            @RequestParam(required = false) String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn);
        if (StringUtils.isEmpty(requestUid)) {
            requestUid = eventRequestBroker.createNewStyleEmptyVote(user.getUid(), request);
        }
        cacheManager.putUssdMenuForUser(msisdn, saveVoteMenu("type", requestUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "type", promptKey, user));
        menu.addMenuOption(menuUrl("yes_no", requestUid),
                getMessage(thisSection, "type", optionsKey + "yesno", user));
        menu.addMenuOption(menuUrl("multi_option/start", requestUid),
                getMessage(thisSection, "type", optionsKey + "multi", user));
        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "yes_no")
    @ResponseBody
    public Request yesNoSelectGroup(@RequestParam String msisdn, @RequestParam String requestUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveVoteMenu("yes_no", requestUid));
        // if the user only has one group, that gets passed in
        final String timePrompt = getMessage(thisSection, "time", promptKey + ".yesno", user);
        return menuBuilder(groupMenu(user, timePrompt, requestUid));
    }

    @RequestMapping(value = path + "closing")
    @ResponseBody
    public Request selectTime(@RequestParam String msisdn, @RequestParam String requestUid,
                              @RequestParam(required = false) String groupUid) throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveVoteMenu("closing", requestUid)
                + (groupUid != null  ? "&groupUid=" + groupUid : ""));
        if (groupUid != null) {
            eventRequestBroker.updateVoteGroup(user.getUid(), requestUid, groupUid);
        }
        final String prompt = getMessage(thisSection, "time", promptKey, user);
        return menuBuilder(timeMenu(user, prompt, requestUid));
    }

    @RequestMapping(value = path + "multi_option/start")
    @ResponseBody
    public Request initiateMultiOption(@RequestParam String msisdn, @RequestParam String requestUid)
            throws URISyntaxException {
        User user = userManager.findByInputNumber(msisdn, saveVoteMenu("multi_option/start", requestUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "multi", promptKey + ".start", user),
                menuUrl("multi_option/add", requestUid));
        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "multi_option/add")
    @ResponseBody
    public Request addVoteOption(@RequestParam String msisdn, @RequestParam String requestUid,
                                 @RequestParam String request,
                                 @RequestParam(required = false) String priorInput) throws URISyntaxException {
        String userInput = StringUtils.isEmpty(priorInput) ? request : priorInput;
        User user = userManager.findByInputNumber(msisdn,
                saveVoteMenu("multi_option/add", requestUid) + "&priorInput=" + encodeParameter(userInput));
        // watch for duplication but service & core should both catch it
        int numberOptions = eventRequestBroker.load(requestUid).getVoteOptions().size();
        if (numberOptions > 1 && "0".equals(userInput.trim())) {
            final String timePrompt = getMessage(thisSection, "time", promptKey + ".multi", user);
            return menuBuilder(groupMenu(user, timePrompt, requestUid));
        } else {
            int newNumber = eventRequestBroker.addVoteOption(user.getUid(), requestUid, userInput);
            final String prompt = newNumber > 1 ?
                    getMessage(thisSection, "multi", promptKey + ".more", user):
                    getMessage(thisSection, "multi", promptKey + ".1more", user);
            USSDMenu menu = new USSDMenu(prompt, menuUrl("multi_option/add", requestUid));
            return menuBuilder(menu);
        }
    }

    private USSDMenu groupMenu(User user, String timePrompt, String requestUid) throws URISyntaxException {
        int possibleGroups = permissionBroker.countActiveGroupsWithPermission(user, GROUP_PERMISSION_CREATE_GROUP_VOTE);
        if (possibleGroups == 1) {
            Group group = permissionBroker.getActiveGroupsSorted(user, GROUP_PERMISSION_CREATE_GROUP_VOTE).get(0);
            eventRequestBroker.updateVoteGroup(user.getUid(), requestUid, group.getUid());
            return timeMenu(user, timePrompt, requestUid);
        } else {
            return groupUtil.askForGroup(new USSDGroupUtil
                    .GroupMenuBuilder(user, thisSection)
                    .messageKey("group")
                    .urlForExistingGroup("closing?requestUid=" + requestUid));
        }
    }

    private USSDMenu timeMenu(User user, String prompt, String requestUid) {
        USSDMenu menu = new USSDMenu(prompt);

        String nextUrl = voteMenus + "confirm?requestUid=" + requestUid + "&field=standard&time=";
        String optionKey = voteKey + ".time." + optionsKey;

        menu.addMenuOption(nextUrl + INSTANT.name(), getMessage(optionKey + "instant", user));
        menu.addMenuOption(nextUrl + HOUR.name(), getMessage(optionKey + "hour", user));
        menu.addMenuOption(nextUrl + DAY.name(), getMessage(optionKey + "day", user));
        menu.addMenuOption(nextUrl + WEEK.name(), getMessage(optionKey + "week", user));
        menu.addMenuOption(voteMenus + "time_custom?requestUid=" + requestUid, getMessage(optionKey + "custom", user));
        return menu;
    }

    @RequestMapping(value = path + "time_custom")
    @ResponseBody
    public Request customVotingTime(@RequestParam String msisdn,
                                    @RequestParam String requestUid) throws URISyntaxException {

        User user = userManager.findByInputNumber(msisdn, saveVoteMenu("time_custom", requestUid));
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "time", promptKey + "-custom", user));
        menu.setFreeText(true);
        menu.setNextURI(voteMenus + "confirm?requestUid=" + requestUid + "&field=custom");
        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "confirm")
    @ResponseBody
    public Request confirmVoteSend(@RequestParam String msisdn, @RequestParam String requestUid,
                                   @RequestParam String request,
                                   @RequestParam(required = false) String priorInput,
                                   @RequestParam(required = false) String field,
                                   @RequestParam(required = false) VoteTime time,
                                   @RequestParam(required = false) Boolean interrupted) throws URISyntaxException {
        final String userInput = StringUtils.isEmpty(priorInput) ? request : priorInput;
        User user = userManager.findByInputNumber(msisdn, saveVoteMenu("confirm", requestUid));
        String lastMenu = field == null ? "standard" : field;

        if (interrupted == null || !interrupted) {
            if ("standard".equals(lastMenu)) {
                setStandardTime(requestUid, time, user);
            } else if ("custom".equals(lastMenu)) {
                setCustomTime(requestUid, userInput, user);
            } else if ("subject".equals(lastMenu)) {
                adjustSubject(requestUid, userInput, user);
            }
        }

        EventRequest vote = eventRequestBroker.load(requestUid);
        String[] promptFields = new String[]{vote.getName(), "at " + vote.getEventDateTimeAtSAST().format(dateTimeFormat)};

        // note: for the moment, not allowing revision of options, because somewhat fiddly (will
        // add if demand arises)
        USSDMenu menu = new USSDMenu(getMessage(thisSection, "confirm", promptKey, promptFields, user));
        menu.addMenuOption(voteMenus + "send?requestUid=" + requestUid, getMessage(thisSection, "confirm", optionsKey + "yes", user));
        menu.addMenuOption(backVoteUrl("subject", requestUid), getMessage(thisSection, "confirm", optionsKey + "topic", user));
        menu.addMenuOption(backVoteUrl("closing", requestUid), getMessage(thisSection, "confirm", optionsKey + "time", user));

        return menuBuilder(menu);
    }

    @RequestMapping(value = path + "send")
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