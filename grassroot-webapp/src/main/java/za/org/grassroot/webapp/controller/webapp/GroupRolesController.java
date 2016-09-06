package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Created by luke on 2016/09/06.
 */
@Controller
@RequestMapping("/group/roles/")
public class GroupRolesController extends BaseController {

	@Autowired
	GroupBroker groupBroker;

	private final static List<Permission> permissionsToDisplay = Arrays.asList(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
			Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
			Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
			Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
			Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS,
			Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
			Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK,
			Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
			Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
			Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
			Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
			Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE);

	/**
	 * Group role view pages
	 */

	@RequestMapping(value = "roles/members")
	public String viewMemberRoles(Model model, @RequestParam String groupUid) {
		// service layer will take care of checking permissions, but at least here make sure user is in group
		Group group = groupBroker.load(groupUid);
		User user = userManagementService.load(getUserProfile().getUid());

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS); // since actually changing will check in services

		List<MembershipInfo> members = new ArrayList<>(MembershipInfo.createFromMembers(group.getMemberships()));
		members.sort(Comparator.reverseOrder());

		model.addAttribute("group", group);
		model.addAttribute("listOfMembers", members);

		model.addAttribute("permissionsImplemented", permissionsToDisplay);
		model.addAttribute("ordinaryPermissions", permissionBroker.getPermissions(group, BaseRoles.ROLE_ORDINARY_MEMBER));
		model.addAttribute("committeePermissions", permissionBroker.getPermissions(group, BaseRoles.ROLE_COMMITTEE_MEMBER));
		model.addAttribute("organizerPermissions", permissionBroker.getPermissions(group, BaseRoles.ROLE_GROUP_ORGANIZER));

		model.addAttribute("canChangePermissions", permissionBroker.isGroupPermissionAvailable(getUserProfile(), group,
				Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE));

		return "group/roles/view";
	}

	@RequestMapping(value = "roles/members", method = RequestMethod.POST)
	public String alterMemberRoles(Model model, @RequestParam String groupUid, @RequestParam String msisdn,
	                               @RequestParam(name = "new_role") String newRole, HttpServletRequest request) {

		User memberToChange = userManagementService.findByInputNumber(msisdn);
		groupBroker.updateMembershipRole(getUserProfile().getUid(), groupUid, memberToChange.getUid(), newRole);
		String[] labels = new String[] { memberToChange.nameToDisplay(), getMessage("group.role." + newRole).toLowerCase() };
		addMessage(model, MessageType.SUCCESS, "group.update.roles.done", labels, request);
		return viewMemberRoles(model, groupUid);

	}

	@RequestMapping(value = "roles/permissions")
	public String viewRolePermissions(Model model, @RequestParam String groupUid) {

		Group group = groupBroker.load(groupUid);
		User user = userManagementService.load(getUserProfile().getUid());

		permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS);

		// todo : clean this up
		List<Permission> permissionsHidden = new ArrayList<>(Arrays.asList(Permission.values()));
		permissionsHidden.removeAll(permissionsToDisplay);

		model.addAttribute("group", group);
		model.addAttribute("ordinaryPermissions", permissionBroker.getPermissions(group, BaseRoles.ROLE_ORDINARY_MEMBER));
		model.addAttribute("committeePermissions", permissionBroker.getPermissions(group, BaseRoles.ROLE_COMMITTEE_MEMBER));
		model.addAttribute("organizerPermissions", permissionBroker.getPermissions(group, BaseRoles.ROLE_GROUP_ORGANIZER));

		model.addAttribute("permissionsImplemented", permissionsToDisplay);
		model.addAttribute("permissionsHidden", permissionsHidden);

		return "group/roles/permissions";
	}

	@RequestMapping(value = "roles/permissions", method = RequestMethod.POST)
	public String changeGroupRole(Model model, @RequestParam String groupUid, HttpServletRequest request) {

		// todo: there must be a more efficient way to do this, possibly via a permission wrapper?

		Set<Permission> ordinaryPermissions = new HashSet<>();
		Set<Permission> committeePermissions = new HashSet<>();
		Set<Permission> organizerPermissions = new HashSet<>();
		Map<String, Set<Permission>> newPermissionMap = new HashMap<>();

		for (Permission permission : permissionsToDisplay) {
			String ordinary = request.getParameter("ordinary_" + permission.getName());
			String committee = request.getParameter("committee_" + permission.getName());
			String organizer = request.getParameter("organizer_" + permission.getName());

			if (ordinary != null && ordinary.equals("on")) ordinaryPermissions.add(permission);
			if (committee != null && committee.equals("on")) committeePermissions.add(permission);
			if (organizer != null && organizer.equals("on")) organizerPermissions.add(permission);
		}

		newPermissionMap.put(BaseRoles.ROLE_ORDINARY_MEMBER, ordinaryPermissions);
		newPermissionMap.put(BaseRoles.ROLE_COMMITTEE_MEMBER, committeePermissions);
		newPermissionMap.put(BaseRoles.ROLE_GROUP_ORGANIZER, organizerPermissions);

		groupBroker.updateGroupPermissions(getUserProfile().getUid(), groupUid, newPermissionMap);

		addMessage(model, MessageType.SUCCESS, "group.role.done", request);
		return viewRolePermissions(model, groupUid);
	}

}
