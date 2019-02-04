package za.org.grassroot.core.domain.group;

import org.hibernate.annotations.Immutable;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.Permission;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Just a bundler that is suposed to be used internally in Group, not exposed via its API
 */
@Embeddable
@Immutable
public class GroupRolePermission {
	@Column(name = "role", nullable = false, length = 50)
	@Enumerated(EnumType.STRING)
	private GroupRole role;

	@Column(name = "permission", nullable = false, length = 100)
	@Enumerated(EnumType.STRING)
	private Permission permission;

	private GroupRolePermission() {
		// for JPA
	}

	public GroupRolePermission(GroupRole role, Permission permission) {
		this.role = role;
		this.permission = permission;
	}

	public GroupRole getRole() {
		return role;
	}

	public Permission getPermission() {
		return permission;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GroupRolePermission)) {
			return false;
		}
		GroupRolePermission that = (GroupRolePermission) o;
		return role == that.role &&
				permission == that.permission;
	}

	@Override
	public int hashCode() {
		return Objects.hash(role, permission);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", GroupRolePermission.class.getSimpleName() + "[", "]")
				.add("role=" + role)
				.add("permission=" + permission)
				.toString();
	}
}
