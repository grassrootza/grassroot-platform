package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.TaskDTO;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.GroupJoinRequestService;
import za.org.grassroot.services.GroupLocationFilter;
import za.org.grassroot.services.TaskBroker;
import za.org.grassroot.services.exception.RequestorAlreadyPartOfGroupException;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by luke on 2016/09/06.
 */
@Controller
@RequestMapping("/group/search/")
public class GroupSearchController extends BaseController {

	private static final Logger log = LoggerFactory.getLogger(GroupSearchController.class);

	@Autowired
	private GroupBroker groupBroker;

	@Autowired
	private TaskBroker taskBroker;

	@Autowired
	private GroupJoinRequestService groupJoinRequestService;

	// todo: prevent a Dos on this / token cycling

	@RequestMapping(value = "/")
	public String searchForGroup(Model model, @RequestParam String term, HttpServletRequest request) {

		if (term.isEmpty()) {
			addMessage(model, BaseController.MessageType.ERROR, "search.error.empty", request);
			model.addAttribute("externalGroupFound", false);
			return "group/results";
		} else {
			String tokenSearch = term.contains("*134*1994*") ?
					term.substring("*134*1994*".length(), term.length() - 1) : term;
			log.info("searching for group ... token to use ... " + tokenSearch);
			Group groupByToken = groupBroker.findGroupFromJoinCode(tokenSearch);
			if (groupByToken != null) {
				model.addAttribute("group", groupByToken);
				model.addAttribute("externalGroupsFound", true);
			} else {
				// just for testing since no UI support yet exists...
				// GroupLocationFilter locationFilter = new GroupLocationFilter(new GeoLocation(45.567641, 18.701211), 30000, true);
				GroupLocationFilter locationFilter = null;
				List<Group> publicGroups = groupBroker.findPublicGroups(getUserProfile().getUid(), term, locationFilter, false);
				model.addAttribute("groupCandidates", publicGroups);
				model.addAttribute("externalGroupsFound", !publicGroups.isEmpty());
			}
			final String userUid = getUserProfile().getUid();
			List<Group> memberGroups = groupBroker.searchUsersGroups(userUid, term);
			List<TaskDTO> memberTasks = taskBroker.searchForTasks(userUid, term);
			model.addAttribute("foundGroups", memberGroups);
			model.addAttribute("foundTasks", memberTasks);
		}

		return "group/results";
	}

	@RequestMapping(value = "join/request", method = RequestMethod.POST)
	public String requestToJoinGroup(Model model, @RequestParam(value="uid") String groupToJoinUid,
	                                 @RequestParam(value="description", required = false) String description,
	                                 HttpServletRequest request, RedirectAttributes attributes) {

		// dealing with Jquery weirdness that has crept in on Chrome ...

		if ("error".equals(groupToJoinUid)) {
			addMessage(attributes, MessageType.ERROR, "group.join.request.error", request);
			return "redirect:/home";
		} else {
			try {
				groupJoinRequestService.open(getUserProfile().getUid(), groupToJoinUid, description);
				addMessage(attributes, MessageType.INFO, "group.join.request.done", request);
				return "redirect:/home";
			} catch (RequestorAlreadyPartOfGroupException e) {
				addMessage(attributes, MessageType.INFO, "group.join.request.member", request);
				attributes.addAttribute("groupUid", groupToJoinUid);
				return "redirect:/group/view";
			}
		}
	}

	@RequestMapping(value = "join/approve")
	public String approveJoinRequest(RedirectAttributes attributes, @RequestParam String requestUid, HttpServletRequest request) {
		// note: join request service will do the permission checking etc and throw an error before proceeding
		groupJoinRequestService.approve(getUserProfile().getUid(), requestUid);
		addMessage(attributes, MessageType.INFO, "group.join.request.approved", request);
		attributes.addAttribute("groupUid", groupJoinRequestService.loadRequest(requestUid).getGroup().getUid());
		return "redirect:/group/view";
	}

	@RequestMapping(value = "join/decline")
	public String declineJoinRequest(@RequestParam String requestUid, HttpServletRequest request, RedirectAttributes attributes) {
		groupJoinRequestService.decline(getUserProfile().getUid(), requestUid);
		addMessage(attributes, MessageType.INFO, "group.join.request.declined", request);
		return "redirect:/home"; // no point showing group if decline request, want to get on with life
	}

	@RequestMapping(value = "join/token", method = RequestMethod.POST)
	public String joinGroup(RedirectAttributes attributes, @RequestParam String groupUid, @RequestParam String token, HttpServletRequest request) {
		groupBroker.addMemberViaJoinCode(getUserProfile().getUid(), groupUid, token);
		addMessage(attributes, MessageType.SUCCESS, "group.join.success", request);
		attributes.addAttribute("groupUid", groupUid);
		return "redirect:/group/view";
	}


}
