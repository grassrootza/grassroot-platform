package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface RoleRepository extends CrudRepository<Role,Long> {

}
