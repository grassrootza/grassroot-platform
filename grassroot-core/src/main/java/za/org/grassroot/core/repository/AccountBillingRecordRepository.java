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

    AccountBillingRecord findOneByPaymentId(String paymentId);

    Set<AccountBillingRecord> findByUidIn(Set<String> uids);

    AccountBillingRecord findTopByAccountOrderByCreatedDateTimeDesc(Account account);

    List<AccountBillingRecord> findByAccountAndStatementDateTimeBetweenAndCreatedDateTimeBefore(Account account, Instant start, Instant end, Instant terminus);

    Set<AccountBillingRecord> findByNextPaymentDateBeforeAndPaidFalse(Instant time);

    List<AccountBillingRecord> findByAccount(Account account, Sort sort);

    int countByAccountAndPaidTrue(Account account);

}
