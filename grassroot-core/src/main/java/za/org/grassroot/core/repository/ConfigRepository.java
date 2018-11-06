package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.ConfigVariable;

import java.util.Optional;

public interface ConfigRepository extends JpaRepository<ConfigVariable, Long> {

    Optional<ConfigVariable> findOneByKey(String key);

}
