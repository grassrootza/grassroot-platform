package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Role;

import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleRepository extends JpaRepository<Role,Long> {

    List<Role> findByName(String name);

    List<Role> findByNameAndRoleType(String name, Role.RoleType roleType);

    Set<Role> findByGroupUid(String groupUid);

    Role findByNameAndGroupUid(String name, String groupUid);
}

