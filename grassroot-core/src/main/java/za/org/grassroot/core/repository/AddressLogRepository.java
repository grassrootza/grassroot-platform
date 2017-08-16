package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.AddressLog;

/**
 * Created by luke on 2017/05/10.
 */
public interface AddressLogRepository extends JpaRepository<AddressLog, Long> {
}
