package za.org.grassroot.webapp.controller.webapp.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import javax.servlet.http.HttpServletRequest;
import java.util.HashSet;

/**
 * Created by luke on 2016/09/06.
 */
@Controller
@RequestMapping("/group/")
@SessionAttributes({"groupCreator"})
public class GroupCreateController extends BaseController {

	private static final Logger logger = LoggerFactory.getLogger(GroupCreateController.class);

	private GroupBroker groupBroker;
	private AccountGroupBroker accountGroupBroker;

	@Autowired
	public GroupCreateController(GroupBroker groupBroker, AccountGroupBroker accountGroupBroker) {
		this.groupBroker = groupBroker;
		this.accountGroupBroker = accountGroupBroker;
	}

	/**
	 * SECTION: Methods to handle view for creating a group
	 */

	@RequestMapping("create")
	public String startGroupIndex(Model model, @RequestParam(value = "parent", required = false) String parentUid) {

		GroupWrapper groupCreator;

		if (parentUid != null) {
			Group parent = groupBroker.load(parentUid);
			groupCreator = new GroupWrapper(parent);
		} else {
			groupCreator = new GroupWrapper();
			MembershipInfo creator = new MembershipInfo(getUserProfile().getPhoneNumber(), BaseRoles.ROLE_GROUP_ORGANIZER,
					getUserProfile().getDisplayName());
			groupCreator.addMember(creator); // to remove ambiguity about group creator being part of group
		}

		model.addAttribute("groupCreator", groupCreator);
		model.addAttribute("canAddToAccount", accountGroupBroker.canAddGroupToAccount(getUserProfile().getUid(), null));

		return "group/create";
	}

	@RequestMapping(value = "create", method = RequestMethod.POST)
	public String createGroup(Model model, @ModelAttribute("groupCreator") @Validated GroupWrapper groupCreator,
	                          BindingResult bindingResult, @RequestParam(required = false) Boolean addToAccount,
							  HttpServletRequest request, RedirectAttributes attributes) {

		Long timeStart, timeEnd;

		logger.info("creating a group, with template = {}", groupCreator.getPermissionTemplate());

		if (bindingResult.hasErrors()) {
			model.addAttribute("groupCreator", groupCreator);
			addMessage(model, BaseController.MessageType.ERROR, "user.enter.error.phoneNumber.invalid", request);
			return "group/create";
		}

		timeStart = System.currentTimeMillis();
		User user = getUserProfile();
		String parentUid = (groupCreator.getHasParent()) ? groupCreator.getParent().getUid() : null;

		Group groupCreated = groupBroker.create(user.getUid(), groupCreator.getGroupName(), parentUid,
				new HashSet<>(groupCreator.getAddedMembers()), groupCreator.getPermissionTemplate(), groupCreator.getGroupDescription(),
				groupCreator.getReminderMinutes(), true, false);
		timeEnd = System.currentTimeMillis();

		logger.info("User load & group creation: {} msecs", timeEnd - timeStart);

		if (addToAccount != null && addToAccount) {
			accountGroupBroker.addGroupToAccount(null, groupCreated.getUid(), getUserProfile().getUid());
		}

		addMessage(attributes, BaseController.MessageType.SUCCESS, "group.creation.success", new Object[]{groupCreated.getGroupName()}, request);
		attributes.addAttribute("groupUid", groupCreated.getUid());
		return "redirect:view";

	}
}
