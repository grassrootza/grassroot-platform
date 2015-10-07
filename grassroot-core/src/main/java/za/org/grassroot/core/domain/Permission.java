package za.org.grassroot.core.domain;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "permission")
@Inheritance(strategy = InheritanceType.JOINED)
public class Permission extends BaseEntity  implements GrantedAuthority {

    public static final String PERMISSION_NAME_SEPARATOR = "_";
    public static final String PERMISSION_NAME_PREFIX    = "PERMISSION";

    public enum PermissionType {STANDARD, GROUP}

    private String         name;
    private Set<Role>      permRoles;
    private Long           groupReferenceId;
    private String         groupReferenceName;
    private PermissionType permissionType;

    public Permission() {
        this.permissionType = PermissionType.STANDARD;
    }

    public Permission(String name) {
        this.name = name;
        this.permissionType = PermissionType.STANDARD;
    }

    public Permission(String name, Long groupReferenceId, String groupReferenceName) {
        this.name = name;
        this.groupReferenceId = groupReferenceId;
        this.groupReferenceName = groupReferenceName;
        this.permissionType = PermissionType.GROUP;
    }

    @Column(name = "permission_name", length = 50)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    @Transient
    public String getAuthority() {
        return Joiner.on(PERMISSION_NAME_PREFIX).join(PERMISSION_NAME_SEPARATOR, name);
    }

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
            joinColumns        = {@JoinColumn(name = "permission_id", referencedColumnName = "id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id")}
    )
    public Set<Role> getPermRoles() {
        return permRoles;
    }

    public void setPermRoles(Set<Role> permRoles) {
        this.permRoles = permRoles;
    }

    @Column(name = "group_reference_id")
    public Long getGroupReferenceId() {
        return groupReferenceId;
    }

    public void setGroupReferenceId(Long groupReferenceId) {
        this.groupReferenceId = groupReferenceId;
    }

    @Column(name = "group_reference_name")
    public String getGroupReferenceName() {
        return groupReferenceName;
    }

    public void setGroupReferenceName(String groupReferenceName) {
        this.groupReferenceName = groupReferenceName;
    }

    @Column(name = "permission_type")
    public PermissionType getPermissionType() {
        return permissionType;
    }

    public void setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType;
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

        if (o instanceof Permission) {
            final Permission other = (Permission) o;
            return  Objects.equal(getAuthority(), other.getAuthority());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getAuthority());
    }
}
