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
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Created by luke on 2015/10/30.
 */
@Controller
@SessionAttributes("vote")
public class VoteController extends BaseController {

    Logger log = LoggerFactory.getLogger(VoteController.class);

    @Autowired
    EventManagementService eventManagementService;

    @Autowired
    GroupManagementService groupManagementService;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @RequestMapping("/vote/create")
    public String createVote(Model model, @RequestParam(value="groupId", required = false) Long groupId) {

        boolean groupSpecified = (groupId != null);
        User user = getUserProfile();
        // Event vote = eventManagementService.createVote(user);

        if (groupSpecified) {
            Group group = groupManagementService.loadGroup(groupId);
            if (!groupManagementService.isUserInGroup(group, getUserProfile()))
                throw new AccessDeniedException("Sorry, you do not have permission to call a vote on this group");
            model.addAttribute("group", group);
        } else {
            // todo: filter for permissions
            model.addAttribute("possibleGroups", groupManagementService.getActiveGroupsPartOf(user));
        }

        model.addAttribute("vote", new Event(user, EventType.Vote, true));
        model.addAttribute("groupSpecified", groupSpecified);

        // log.info("Vote that we are passing: " + vote);

        return "vote/create";
    }

    @RequestMapping(value = "/vote/create", method = RequestMethod.POST)
    public String createVoteDo(Model model, @ModelAttribute("vote") Event vote, BindingResult bindingResult,
                               @RequestParam("selectedGroupId") Long selectedGroupId,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("Vote passed back to us: " + vote);
        Group group = groupManagementService.loadGroup(selectedGroupId);
        if (!groupManagementService.isUserInGroup(group, getUserProfile()))
            throw new AccessDeniedException("");

        // choosing to set some default fields here instead of a lot of hidden fields in Thymeleaf
        vote.setEventType(EventType.Vote);
        vote.setCreatedByUser(getUserProfile());
        vote.setRsvpRequired(true);
        vote.setAppliesToGroup(group);

        log.info("Fleshed out vote: " + vote);

        vote = eventManagementService.createVote(vote);
        log.info("Stored vote, at end of creation: " + vote.toString());

        addMessage(model, MessageType.SUCCESS, "vote.creation.success", request);
        model.addAttribute("eventId", vote.getId());
        return "vote/view";
    }

    @RequestMapping("/vote/view")
    public String viewVote(Model model, @RequestParam Long eventId) {

        Event vote = eventManagementService.loadEvent(eventId);

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
