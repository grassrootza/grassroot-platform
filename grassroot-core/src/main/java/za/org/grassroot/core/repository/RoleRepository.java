package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Role;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleRepository extends CrudRepository<Role,Long> {

    List<Role> findByName(String name);

    List<Role> findByNameAndRoleType(String name, Role.RoleType roleType);

    List<Role> findByGroupReferenceId(Long groupReferenceId);

    Role findByNameAndGroupReferenceId(String name, Long groupReferenceId);
}
