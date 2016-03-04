package za.org.grassroot.core.domain;

import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "role")
public class Role extends BaseEntity implements GrantedAuthority {

    public enum RoleType {
        STANDARD,
        GROUP
    }

    @Column(name = "role_name", nullable = false, length = 50)
    private String name;

    @Column(name = "group_uid", length = 50)
    private String groupUid;

    @Column(name = "role_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private RoleType roleType;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 50)
    private Set<Permission> permissions = new HashSet<>();

    private Role() {
        // for JPA
    }

    public Role(String name) {
        this(name, null);
    }

    public Role(String name, String groupUid) {
        this.name = Objects.requireNonNull(name);
        this.roleType = groupUid == null ? RoleType.STANDARD : RoleType.GROUP;
        this.groupUid = groupUid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Permission> getPermissions() {
        if (permissions == null) {
            permissions = new HashSet<>();
        }
        return new HashSet<>(permissions);
    }

    public void removePermission(Permission permission) {
        Objects.requireNonNull(permission);
        this.permissions.remove(permission);
    }

    public void setPermissions(Set<Permission> permissions) {
        Objects.requireNonNull(permissions);

        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    public String getGroupUid() {
        return groupUid;
    }

    void setGroupUid(String groupUid) {
        this.groupUid = groupUid;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }

    public boolean isGroupRole() {
        return roleType.equals(RoleType.GROUP);
    }
//~=================================================================================================================

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    void setGroup(Group group) {
        this.groupUid = group.getUid();
    }

    // looks like toString() method might be used for other purposes, so creating a helper as a descriptor
    public String describe() {
        return "Role{" +
                "role name='" + name + '\'' +
                ", groupUid ='" + groupUid + '\'' +
                ", id=" + id +
                ", type=" + roleType +
                ", permissions=" + permissions.toString() +
                '}';
    }

    @Override
    public String toString() {
        return getAuthority();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }

        if (o instanceof Role) {
            final Role other = (Role) o;
            return com.google.common.base.Objects.equal(getAuthority(), other.getAuthority())
                    && com.google.common.base.Objects.equal(getId(), other.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getAuthority());
    }

    @Override
    public String getAuthority() {
        return getName();
    }
}
