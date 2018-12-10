package za.org.grassroot.core.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.enums.LiveWireLogType;

import java.util.List;

/**
 * Created by luke on 2017/05/17.
 */
public interface LiveWireLogRepository extends JpaRepository<LiveWireLog, Long>, JpaSpecificationExecutor<LiveWireLog> {

    List<LiveWireLog> findByType(LiveWireLogType type, Pageable pageable);

}
