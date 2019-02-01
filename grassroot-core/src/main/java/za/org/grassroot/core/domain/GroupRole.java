package za.org.grassroot.core.domain;

import org.springframework.security.core.GrantedAuthority;

import java.util.Set;


public enum GroupRole implements GrantedAuthority {
	// maintain the ordering of values since Comparable is taking this into account
	ROLE_GROUP_ORGANIZER(PermissionSets.defaultGroupOrganizerPermissions, PermissionSets.closedGroupOrganizerPermissions),
	ROLE_COMMITTEE_MEMBER(PermissionSets.defaultCommitteeMemberPermissions, PermissionSets.closedCommitteeMemberPermissions),
	ROLE_ORDINARY_MEMBER(PermissionSets.defaultOrdinaryMemberPermissions, PermissionSets.closedOrdinaryMemberPermissions);

	private final Set<Permission> defaultGroupPermissions;
	private final Set<Permission> closedGroupPermissions;

	GroupRole(Set<Permission> defaultGroupPermissions, Set<Permission> closedGroupPermissions) {
		this.defaultGroupPermissions = defaultGroupPermissions;
		this.closedGroupPermissions = closedGroupPermissions;
	}

	public Set<Permission> getDefaultGroupPermissions() {
		return defaultGroupPermissions;
	}

	public Set<Permission> getClosedGroupPermissions() {
		return closedGroupPermissions;
	}


	@Override
	public String getAuthority() {
		return name();
	}
}
