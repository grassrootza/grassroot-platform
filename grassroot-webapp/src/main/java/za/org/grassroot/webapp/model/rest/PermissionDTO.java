package za.org.grassroot.webapp.model.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;
import org.springframework.context.support.MessageSourceAccessor;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;

import java.util.Locale;
import java.util.Set;

/**
 * Created by luke on 2016/07/18.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PermissionDTO implements Comparable<PermissionDTO> {

	private final static ImmutableMap<Permission, Integer> displayedPermissionsSorted = ImmutableMap.<Permission, Integer>builder()
			.put(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS, 1)
			.put(Permission.GROUP_PERMISSION_CREATE_GROUP_MEETING, 2)
			.put(Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 3)
			.put(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 4)
			.put(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER, 5)
			.put(Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER, 6)
			.put(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS, 7)
			.put(Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE, 8)
			.put(Permission.GROUP_PERMISSION_CLOSE_OPEN_LOGBOOK, 9)
			.put(Permission.GROUP_PERMISSION_VIEW_MEETING_RSVPS, 10)
			.put(Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS, 11)
			.build();


	private String groupUid;
	private String forRole;

	private Permission permission;
	private String permissionName;
	private String permissionLabel;
	private String permissionDesc;
	private boolean permissionEnabled;
	private int position;

	public PermissionDTO() { } // for Jackson

	public PermissionDTO(Permission permission, Group group, String roleName, Set<Permission> enabledSet, MessageSourceAccessor messageSourceAccessor) {
		this.permission = permission;
		this.groupUid = group.getUid();
		this.forRole = roleName;
		this.permissionName = permission.getName();
		this.permissionLabel = messageSourceAccessor.getMessage("rest.permission." + permission.getName(), Locale.getDefault());
		this.permissionDesc = messageSourceAccessor.getMessage("rest.permission.desc." + permission.getName(), Locale.getDefault());
		this.permissionEnabled = enabledSet.contains(permission);
		this.position = displayedPermissionsSorted.get(permission);
	}

	public String getGroupUid() {
		return groupUid;
	}

	public void setGroupUid(String groupUid) {
		this.groupUid = groupUid;
	}

	public String getForRole() {
		return forRole;
	}

	public void setForRole(String forRole) {
		this.forRole = forRole;
	}

	public Permission getPermission() {
		if (permission == null && permissionName != null && !permissionName.isEmpty()) {
			permission = Permission.valueOf(permissionName);
		}
		return permission;
	}

	public void setPermission(Permission permission) { this.permission = permission; }

	public String getPermissionName() {
		return permissionName;
	}

	public void setPermissionName(String permissionName) {
		this.permissionName = permissionName;
	}

	public String getPermissionLabel() {
		return permissionLabel;
	}

	public void setPermissionLabel(String permissionLabel) {
		this.permissionLabel = permissionLabel;
	}

	public String getPermissionDesc() {
		return permissionDesc;
	}

	public void setPermissionDesc(String permissionDesc) {
		this.permissionDesc = permissionDesc;
	}

	public boolean isPermissionEnabled() {
		return permissionEnabled;
	}

	public void setPermissionEnabled(boolean permissionEnabled) {
		this.permissionEnabled = permissionEnabled;
	}

	public int getPosition() { return position; }

	@Override
	public String toString() {
		return "PermissionDTO{" +
				"groupUid='" + groupUid + '\'' +
				", forRole='" + forRole + '\'' +
				", permission='" + getPermission() + '\'' +
				", permissionName='" + permissionName + '\'' +
				", permissionLabel='" + permissionLabel + '\'' +
				", permissionDesc='" + permissionDesc + '\'' +
				", permissionEnabled=" + permissionEnabled +
				'}';
	}

	@Override
	public int compareTo(PermissionDTO permissionDTO) {
		return (this.position > permissionDTO.position) ? 1
				: (this.position == permissionDTO.position) ? 0 : -1;
	}
}
