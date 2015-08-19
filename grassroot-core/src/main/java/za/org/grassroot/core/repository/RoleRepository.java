package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Role;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleRepository extends CrudRepository<Role,Long> {
}
