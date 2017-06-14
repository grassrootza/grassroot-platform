package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.enums.VoteType;
import za.org.grassroot.webapp.model.web.VoteWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;

/**
 * Created by luke on 2015/10/30.
 */
@Controller
@RequestMapping("/vote/")
@SessionAttributes("vote")
public class VoteController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(VoteController.class);

    private final VoteBroker voteBroker;
    private final EventBroker eventBroker;
    private final GroupBroker groupBroker;
    private final AccountGroupBroker accountGroupBroker;

    @Autowired
    public VoteController(EventBroker eventBroker, GroupBroker groupBroker, EventLogBroker eventLogBroker,
                          AccountGroupBroker accountGroupBroker, VoteBroker voteBroker) {
        this.voteBroker = voteBroker;
        this.eventBroker = eventBroker;
        this.groupBroker = groupBroker;
        this.accountGroupBroker = accountGroupBroker;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAutoGrowCollectionLimit(2048);
    }

    @RequestMapping("create")
    public String createVote(Model model, @RequestParam(value="groupUid", required = false) String groupUid,
                             RedirectAttributes attributes, HttpServletRequest request) {

        boolean groupSpecified = !StringUtils.isEmpty(groupUid);
        User user = userManagementService.load(getUserProfile().getUid()); // in case permissions changed, call entity from DB

        if (permissionBroker.countActiveGroupsWithPermission(getUserProfile(), GROUP_PERMISSION_CREATE_GROUP_VOTE) == 0) {
            addMessage(attributes, MessageType.INFO, "vote.create.group", request);
            return "redirect:/group/create";
        }

        VoteWrapper voteWrapper = VoteWrapper.makeEmpty();
        if (groupSpecified) {
            Group group = groupBroker.load(groupUid);
            permissionBroker.validateGroupPermission(user, group, GROUP_PERMISSION_CREATE_GROUP_VOTE); // double check, given sensitivity
            voteWrapper.setParentUid(groupUid);
            model.addAttribute("group", group);
            model.addAttribute("eventsLeft", accountGroupBroker.numberEventsLeftForGroup(groupUid));
            addFlagsToModel(model, user, group);
        } else {
            addFlagsToModel(model, user, null);
            model.addAttribute("eventsLeft", 99);
            model.addAttribute("possibleGroups", permissionBroker.getActiveGroupsSorted(user, GROUP_PERMISSION_CREATE_GROUP_VOTE));
        }

        model.addAttribute("vote", voteWrapper);

        return "vote/create";
    }

    private void addFlagsToModel(Model model, User user, Group group) {
        model.addAttribute("thisGroupPaidFor", group != null && group.isPaidFor());
        model.addAttribute("accountAdmin", user.getPrimaryAccount() != null);
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public String createVoteDo(Model model, @ModelAttribute("vote") VoteWrapper vote, BindingResult bindingResult,
                               @RequestParam(value = "selectedGroupUid", required = false) String selectedGroupUid,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {

        String groupUid = (selectedGroupUid == null) ? vote.getParentUid() : selectedGroupUid;

        try {
            // since user might have added options then switched back to yes/no, can't just pass getOptions
            List<String> voteOptions = VoteType.YES_NO.equals(vote.getType()) ? null : vote.getOptions();
            eventBroker.createVote(getUserProfile().getUid(), groupUid, JpaEntityType.GROUP, vote.getTitle(), vote.getEventDateTime(),
                    vote.isIncludeSubGroups(), vote.getDescription(), Collections.emptySet(), voteOptions);
            addMessage(redirectAttributes, MessageType.SUCCESS, "vote.creation.success", request);
            redirectAttributes.addAttribute("groupUid", groupUid);
            return "redirect:/group/view";
        } catch (EventStartTimeNotInFutureException e) {
            addMessage(model, MessageType.ERROR, "vote.creation.time.error", request);
            Group group = groupBroker.load(groupUid);
            vote.setParentUid(groupUid);
            model.addAttribute("group", group);
            return "vote/create";
        } catch (AccountLimitExceededException e) {
            User user = userManagementService.load(getUserProfile().getUid());
            addMessage(model, MessageType.ERROR, "vote.creation.limit.error", request);
            model.addAttribute("possibleGroups", permissionBroker.getActiveGroupsSorted(user, GROUP_PERMISSION_CREATE_GROUP_VOTE));
            addFlagsToModel(model, user, null);
            return "vote/create";
        }
    }

    @RequestMapping("view")
    public String viewVote(Model model, @RequestParam String eventUid, @RequestParam(required = false) SourceMarker source) {

        Vote vote = voteBroker.load(eventUid);
        boolean canModify = (vote.getCreatedByUser().equals(getUserProfile())
                && vote.getEventStartDateTime().isAfter(Instant.now())); // todo: make this more nuanced

        Map<String, Long> voteTotals = voteBroker.fetchVoteResults(getUserProfile().getUid(), eventUid);
        long totalResponses = voteTotals.values().stream().mapToLong(Long::longValue).sum();
        long possibleResponses = vote.getAllMembers().size();

        model.addAttribute("vote", vote);
        model.addAttribute("voteTotals", voteTotals);
        model.addAttribute("noReply", possibleResponses - totalResponses);
        model.addAttribute("replied", totalResponses);
        model.addAttribute("possible", possibleResponses);

        model.addAttribute("canModify", canModify);
        model.addAttribute("reminderOptions", EventReminderType.values());
        model.addAttribute("customReminderOptions", reminderMinuteOptions(false));

        model.addAttribute("fromGroup", SourceMarker.GROUP.equals(source));
        model.addAttribute("parentUid", vote.getParent().getUid());

        return "vote/view";
    }

    @RequestMapping(value = "description", method = RequestMethod.POST)
    public String changeDescription(@RequestParam String eventUid, @RequestParam String description, RedirectAttributes attributes, HttpServletRequest request) {
        eventBroker.updateDescription(getUserProfile().getUid(), eventUid, description);
        addMessage(attributes, MessageType.SUCCESS, "vote.update.done", request);
        attributes.addAttribute("eventUid", eventUid);
        return "redirect:/vote/view";
    }

    @RequestMapping(value = "changedatetime", method = RequestMethod.POST)
    public String changeDateTime(@RequestParam String eventUid, @RequestParam LocalDateTime closingDateTime,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        // note: this is pretty much the same as above, but may make it more sophisticated later, so am keeping separate
        eventBroker.updateVoteClosingTime(getUserProfile().getUid(), eventUid, closingDateTime);
        String toDisplay = DateTimeFormatter.ofPattern("h:mm a' on 'E, d MMMM").format(closingDateTime);
        addMessage(attributes, MessageType.SUCCESS, "vote.update.closing.done", new String[] {toDisplay}, request);
        attributes.addAttribute("eventUid", eventUid);
        return "redirect:/vote/view";
    }

    @RequestMapping(value = "reminder", method = RequestMethod.POST)
    public String changeReminderSettings(@RequestParam String eventUid, @RequestParam EventReminderType reminderType,
                                         @RequestParam(value = "custom_minutes", required = false) Integer customMinutes,
                                         RedirectAttributes attributes, HttpServletRequest request) {

        int minutes = (customMinutes != null && reminderType.equals(EventReminderType.CUSTOM)) ? customMinutes : 0;
        eventBroker.updateReminderSettings(getUserProfile().getUid(), eventUid, reminderType, minutes);
        Instant newScheduledTime = eventBroker.load(eventUid).getScheduledReminderTime();
        if (newScheduledTime != null) {
            // todo: make sure this actually works properly with zones, on AWS in Ireland, etc ...
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a' on 'E, d MMMM").withZone(ZoneId.systemDefault());
            String reminderTime = formatter.format(newScheduledTime);
            addMessage(attributes, MessageType.SUCCESS, "vote.reminder.changed", new String[] {reminderTime}, request);
        } else {
            addMessage(attributes, MessageType.SUCCESS, "vote.reminder.none", request);
        }
        attributes.addAttribute("eventUid", eventUid);
        return "redirect:/vote/view";
    }

    @RequestMapping(value = "answer", method = RequestMethod.GET)
    public String answerVote(@RequestParam String eventUid, @RequestParam String answer,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Vote vote = voteBroker.load(eventUid);
        User sessionUser = getUserProfile();
        String priorUrl = request.getHeader(HttpHeaders.REFERER);

        log.info("recording vote answer: {}", answer);

        voteBroker.recordUserVote(sessionUser.getUid(), eventUid, answer);

        addMessage(redirectAttributes, MessageType.INFO, "vote.recorded", request);

        if (priorUrl.contains("group")) {
            redirectAttributes.addAttribute("groupUid", vote.getAncestorGroup().getUid());
            return "redirect:/group/view";
        } else if (priorUrl.contains("vote")) {
            redirectAttributes.addAttribute("eventUid", eventUid);
            return "redirect:/vote/view";
        } else {
            return "redirect:/home";
        }

    }

}