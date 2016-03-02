package za.org.grassroot.core.domain;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "role")
@Inheritance(strategy = InheritanceType.JOINED)
public class Role extends BaseEntity implements GrantedAuthority {

    public enum RoleType {
        STANDARD,
        GROUP
    }

    private String name;
    private String groupUid;
    private RoleType roleType;
    private Set<Permission> permissions = new HashSet<>();
    private Set<User> users = new HashSet<>();

    public Role() {
        this.roleType = RoleType.STANDARD;
    }

    public Role(String name) {
        this(name, null);
        this.roleType = RoleType.STANDARD;
    }

    public Role(String name, String groupUid) {
        this.name = Objects.requireNonNull(name);
        this.roleType = groupUid == null ? RoleType.STANDARD : RoleType.GROUP;
        this.groupUid = groupUid;
    }

    @Column(name = "role_name", nullable = false, length = 100)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
            joinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "permission_id", referencedColumnName = "id", unique = false)}
    )
    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns        = {@JoinColumn(name = "role_id", referencedColumnName = "id", unique = false)},
            inverseJoinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id", unique = false)}
    )
    public Set<User> getUsers() {
        if (users == null) {
            users = new HashSet<>();
        }
        return new HashSet<>(users);
    }

    void setUsers(Set<User> users) { this.users = users; }

    @Column(name = "group_uid")
    public String getGroupUid() {
        return groupUid;
    }

    void setGroupUid(String groupUid) {
        this.groupUid = groupUid;
    }

    @Column(name = "role_type")
    @Enumerated(EnumType.STRING)
    public RoleType getRoleType() {
        return roleType;
    }

    void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }
//~=================================================================================================================

    @Transient
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    @Transient
    void setGroup(Group group) {
        this.groupUid = group.getUid();
    }

    // looks like toString() method might be used for other purposes, so creating a helper as a descriptor
    @Transient
    public String describe() {
        return "Role{" +
                "role name='" + name + '\'' +
                ", groupUid ='" + groupUid + '\'' +
                ", id=" + id +
                ", type=" + roleType +
                ", permissions=" + permissions.toString()+
                '}';
    }

    @Override
    public String toString() {

        return getAuthority();

    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;

        if (o instanceof Role) {
            final Role other = (Role) o;
            return com.google.common.base.Objects.equal(getAuthority(), other.getAuthority())
                    &&  com.google.common.base.Objects.equal(getId(), other.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getAuthority());
    }

    @Override
    @Transient
    public String getAuthority() {

        return  getName();

//        switch (roleType) {
//
//            case GROUP:
//                return Joiner.on(ROLE_NAME_SEPARATOR).join(RoleType.GROUP.name(),
//                        ROLE_NAME_PREFIX, getName(), GROUP_ID_PREFIX, getGroupReferenceId());
//            default:
//                return Joiner.on(ROLE_NAME_SEPARATOR).join(RoleType.STANDARD.name(),
//                        ROLE_NAME_PREFIX, getName());
//        }
    }

    @Transient
    public  boolean isGroupRole()
    {
        return roleType.equals(RoleType.GROUP);
    }

}
