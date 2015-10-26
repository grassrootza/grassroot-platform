package za.org.grassroot.core.domain;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "role")
@Inheritance(strategy = InheritanceType.JOINED)
public class Role extends BaseEntity implements GrantedAuthority {

    public static final String ROLE_NAME_SEPARATOR = "_";
    public static final String ROLE_NAME_PREFIX    = "ROLE";
    public static final String GROUP_ID_PREFIX     = "GROUP_ID";

    public enum RoleType {STANDARD, GROUP}

    private String name;
    private Long   groupReferenceId;
    private String groupReferenceName;
    private RoleType roleType;

    private Set<Permission> permissions = new HashSet<>();


    public Role() {
        this.roleType = RoleType.STANDARD;
    }

    public Role(String name) {
        this.name = name;
        this.roleType = RoleType.STANDARD;
    }


    public Role(String name, Long groupReferenceId, String groupReferenceName) {

        Assert.notNull(name);
        Assert.notNull(groupReferenceId);
        Assert.notNull(groupReferenceName);

        this.name = name;
        this.groupReferenceId = groupReferenceId;
        this.groupReferenceName = formatGroupNameReference(groupReferenceName);
        this.roleType = RoleType.GROUP;
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

    @Column(name = "group_reference_id")
    public Long getGroupReferenceId() {
        return groupReferenceId;
    }

    @Column(name = "group_reference_name")
    public String getGroupReferenceName() {
        return groupReferenceName;
    }

    @Column(name = "role_type")
    @Enumerated(EnumType.STRING)
    public RoleType getRoleType() {
        return roleType;
    }

    public void setGroupReferenceId(Long groupReferenceId) {
        this.groupReferenceId = groupReferenceId;
    }

    public void setGroupReferenceName(String groupReferenceName) {
        this.groupReferenceName = formatGroupNameReference(groupReferenceName);
    }

    public void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }
//~=================================================================================================================


    private String formatGroupNameReference(String groupName) {
        if (groupName != null) {
            return CharMatcher.JAVA_LETTER_OR_DIGIT.retainFrom(groupName);
        }
        return "";
    }

    @Transient
    public void addPermission(Permission permission) {
        this.permissions.add(permission);
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
            return Objects.equal(getAuthority(), other.getAuthority())
                    &&  Objects.equal(getId(), other.getId());
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
        return roleType.equals(RoleType.GROUP) && groupReferenceId != null;
    }

}
