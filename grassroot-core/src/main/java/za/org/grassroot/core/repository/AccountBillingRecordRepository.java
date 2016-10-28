package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingRecordRepository extends JpaRepository<AccountBillingRecord, Long> {

    AccountBillingRecord findOneByUid(String uid);

    Set<AccountBillingRecord> findByUidIn(Set<String> uids);

    AccountBillingRecord findOneByAccountOrderByCreatedDateTimeDesc(Account account);

    Set<AccountBillingRecord> findByNextPaymentDateBeforeAndPaidFalse(Instant time);

    List<AccountBillingRecord> findByAccount(Account account, Sort sort);

}
