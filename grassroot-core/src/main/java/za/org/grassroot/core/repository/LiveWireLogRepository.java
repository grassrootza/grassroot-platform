package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.livewire.LiveWireLog;

/**
 * Created by luke on 2017/05/17.
 */
public interface LiveWireLogRepository extends JpaRepository<LiveWireLog, Long>, JpaSpecificationExecutor<LiveWireLog> {
}
