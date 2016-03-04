package za.org.grassroot.core.domain;

import com.google.common.base.Objects;
import org.springframework.security.acls.domain.AclFormattingUtils;
import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
@Entity
@Table(name = "permission")
@Inheritance(strategy = InheritanceType.JOINED)
public class Permission extends BaseEntity implements GrantedAuthority, org.springframework.security.acls.model.Permission {

    public static final String PERMISSION_NAME_SEPARATOR = "_";
    public static final String PERMISSION_NAME_PREFIX    = "PERMISSION";

    private String         name;
    private Set<Role>      permRoles;

    private char code = '*';
    private int mask;

    public Permission() {
    }

    public Permission(String name) {
        this.name = name;
    }


    public Permission(String name, int mask) {
        this.name = name;
        this.mask = mask;
    }

    @Column(name = "permission_name", nullable = false,length = 50, unique = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    @Transient
    public String getAuthority() {
        return name;
    }

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permissions",
            joinColumns = {@JoinColumn(name = "permission_id", referencedColumnName = "id")},
            inverseJoinColumns = {@JoinColumn(name = "role_id", referencedColumnName = "id")}
    )
    public Set<Role> getPermRoles() {
        return permRoles;
    }

    public void setPermRoles(Set<Role> permRoles) {
        this.permRoles = permRoles;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    @Override
    @Column ( unique = true)
    public int getMask() {
        return mask;
    }

    @Override
    @Transient
    public String getPattern() {
        return AclFormattingUtils.printBinary(mask, code);
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
            return Objects.equal(getAuthority(), other.getAuthority());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getAuthority());
    }
}
