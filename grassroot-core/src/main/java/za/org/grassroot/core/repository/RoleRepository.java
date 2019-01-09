package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.RoleName;
import za.org.grassroot.core.domain.Role;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleRepository extends JpaRepository<Role,Long> {

    List<Role> findByName(RoleName name);

    List<Role> findByNameAndRoleType(RoleName name, Role.RoleType roleType);
}

