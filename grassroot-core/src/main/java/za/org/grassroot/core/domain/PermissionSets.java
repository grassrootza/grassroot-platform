package za.org.grassroot.core.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PermissionSets {
	private PermissionSets() {
	}

	public static final Set<Permission> defaultOrdinaryMemberPermissions =
			constructPermissionSet(Collections.emptySet(),
					Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
					Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS,
					Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
					Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK);

	public static final Set<Permission> defaultCommitteeMemberPermissions =
			constructPermissionSet(defaultOrdinaryMemberPermissions,
					Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
					Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
					Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
					Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
					Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
					Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
					Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
					Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION,
					Permission.GROUP_PERMISSION_MUTE_MEMBER);

	public static final Set<Permission> defaultGroupOrganizerPermissions =
			constructPermissionSet(defaultCommitteeMemberPermissions,
					Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
					Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
					Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
					Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
					Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
					Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
					Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER,
					Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE,
					Permission.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE,
					Permission.GROUP_PERMISSION_SEND_BROADCAST,
					Permission.GROUP_PERMISSION_CREATE_CAMPAIGN);

	// closed group structure ... again, externalize
	public static final Set<Permission> closedOrdinaryMemberPermissions =
			constructPermissionSet(Collections.emptySet(),
					Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS);

	public static final Set<Permission> closedCommitteeMemberPermissions =
			constructPermissionSet(defaultOrdinaryMemberPermissions,
					Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS,
					Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING,
					Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE,
					Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS,
					Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY,
					Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK,
					Permission.GROUP_PERMISSION_MUTE_MEMBER);

	public static final Set<Permission> closedGroupOrganizerPermissions =
			constructPermissionSet(defaultCommitteeMemberPermissions,
					Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER,
					Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
					Permission.GROUP_PERMISSION_CREATE_SUBGROUP,
					Permission.GROUP_PERMISSION_AUTHORIZE_SUBGROUP,
					Permission.GROUP_PERMISSION_DELEGATE_SUBGROUP_CREATION,
					Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER,
					Permission.GROUP_PERMISSION_FORCE_ADD_MEMBER,
					Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
					Permission.GROUP_PERMISSION_DELINK_SUBGROUP,
					Permission.GROUP_PERMISSION_FORCE_DELETE_MEMBER,
					Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE,
					Permission.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE,
					Permission.GROUP_PERMISSION_SEND_BROADCAST,
					Permission.GROUP_PERMISSION_CREATE_CAMPAIGN);

	// a couple of permissions that we don't let users remove from organizers (since then no one can change them)
	public static final Set<Permission> protectedOrganizerPermissions =
			constructPermissionSet(Collections.emptySet(),
					Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS,
					Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE,
					Permission.GROUP_PERMISSION_FORCE_PERMISSION_CHANGE);

	private static Set<Permission> constructPermissionSet(Set<Permission> baseSet, Permission... permissions) {
		Set<Permission> set = new HashSet<>(baseSet);
		Collections.addAll(set, permissions);
		return java.util.Collections.unmodifiableSet(set);
	}
}
