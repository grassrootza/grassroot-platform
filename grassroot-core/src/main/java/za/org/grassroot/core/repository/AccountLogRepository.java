package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.enums.AccountLogType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/04/03.
 */
public interface AccountLogRepository extends JpaRepository<AccountLog, Long> {
    int countByAccountAndAccountLogTypeAndCreationTimeGreaterThan(Account account, AccountLogType accountLogType, Instant time);
    List<AccountLog> findByBroadcastAndAccountLogType(Broadcast broadcast, AccountLogType type);
}
