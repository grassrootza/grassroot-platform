package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Permission;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface PermissionRepository extends CrudRepository<Permission,Long> {

    Permission findByName(String name);

    List<Permission> findByNameIn(List<String> names);
}
