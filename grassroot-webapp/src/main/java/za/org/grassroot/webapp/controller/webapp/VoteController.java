package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;

/**
 * Created by luke on 2015/10/30.
 */
@Controller
@SessionAttributes("vote")
public class VoteController extends BaseController {

    Logger log = LoggerFactory.getLogger(VoteController.class);

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @RequestMapping("/vote/create")
    public String createVote(Model model, @RequestParam(value="groupUid", required = false) String groupUid) {

        boolean groupSpecified = (groupUid != null);
        User user = getUserProfile();

        VoteRequest voteRequest = VoteRequest.makeEmpty(user, null);
        voteRequest.setRsvpRequired(true);

        if (groupSpecified) {
            Group group = groupBroker.load(groupUid);
            // note: though service layer checks this, and prior UX, want to catch it here too in case of URL hacking
            permissionBroker.validateGroupPermission(user, group, GROUP_PERMISSION_CREATE_GROUP_VOTE);
            model.addAttribute("group", group);
            voteRequest.setAppliesToGroup(group);
        } else {
            // todo: filter for permissions
            model.addAttribute("possibleGroups", permissionBroker.getActiveGroupsWithPermission(getUserProfile(), GROUP_PERMISSION_CREATE_GROUP_VOTE));
        }

        model.addAttribute("vote", voteRequest);
        model.addAttribute("groupSpecified", groupSpecified);
        model.addAttribute("reminderOptions", reminderMinuteOptions());

        return "vote/create";
    }

    @RequestMapping(value = "/vote/create", method = RequestMethod.POST)
    public String createVoteDo(Model model, @ModelAttribute("vote") VoteRequest vote, BindingResult bindingResult,
                               @RequestParam(value = "selectedGroupUid", required = false) String selectedGroupUid,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("Vote passed back to us: " + vote);
        String groupUid = (selectedGroupUid == null) ? vote.getAppliesToGroup().getUid() : selectedGroupUid;

        eventBroker.createVote(getUserProfile().getUid(), groupUid, vote.getName(), vote.getEventStartDateTime(),
                               vote.isIncludeSubGroups(), vote.isRelayable(), vote.getDescription(), Collections.emptySet());

        log.info("Stored vote, at end of creation: " + vote.toString());

        // todo : ask person who called vote for vote (and, in general, add that to group view)
        addMessage(redirectAttributes, MessageType.SUCCESS, "vote.creation.success", request);
        redirectAttributes.addAttribute("groupUid", groupUid);
        return "redirect:/group/view";
    }

    @RequestMapping("/vote/view")
    public String viewVote(Model model, @RequestParam String eventUid) {

        Event vote = eventBroker.load(eventUid);

        Map<String, Integer> voteResults = eventManagementService.getVoteResults(vote);

        model.addAttribute("vote", vote);
        model.addAttribute("yes", voteResults.get("yes"));
        model.addAttribute("no", voteResults.get("no"));
        model.addAttribute("abstained", voteResults.get("abstained"));
        model.addAttribute("possible", voteResults.get("possible"));
        return "vote/view";
    }

    @RequestMapping(value = "/vote/answer", method = RequestMethod.POST)
    public String answerVote(Model model, @RequestParam(value="eventId") Long eventId,
                             @RequestParam(value="answer") String answer, HttpServletRequest request, RedirectAttributes redirectAttributes) {

        Event vote = eventManagementService.loadEvent(eventId);
        User sessionUser = getUserProfile();

        eventLogManagementService.rsvpForEvent(vote, sessionUser, EventRSVPResponse.fromString(answer));

        addMessage(redirectAttributes, MessageType.INFO, "vote.recorded", request);
        return "redirect:/home";

    }

}
