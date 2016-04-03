package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.AccountLog;

import java.util.Set;

/**
 * Created by luke on 2016/04/03.
 */
public interface AccountLogRepository extends JpaRepository<AccountLog, Long> {
    AccountLog findOneByUserUid(String userUid);
    Set<AccountLog> findByGroupUid(String groupUid);
}
