package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.ConfigVariable;

public interface ConfigRepository extends JpaRepository<ConfigVariable, Long> {

    ConfigVariable findOneByKey(String key);

}
