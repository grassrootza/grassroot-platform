package za.org.grassroot.core.domain;

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

    private String name;
    private Set<Role> permRoles;

    public Permission() {
    }

    public Permission(String name) {
        this.name = name;
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
        return name;
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

    @Override
    public String toString() {
        return String.format("%s(id=%d, name='%s')",
                this.getClass().getSimpleName(),
                this.getId(), this.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;

        if (o instanceof Role) {
            final Permission other = (Permission) o;
            return Objects.equal(getId(), other.getId())
                    && Objects.equal(getName(), other.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId(), getName());
    }
}
