package za.org.grassroot.core.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.enums.AccountLogType;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2016/04/03.
 */
public interface AccountLogRepository extends JpaRepository<AccountLog, Long> {
    List<AccountLog> findByBroadcastAndAccountLogType(Broadcast broadcast, AccountLogType type);
    List<AccountLog> findByAccountLogType(AccountLogType type, Pageable pageable);
}
