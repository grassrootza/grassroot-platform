package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;

import java.util.List;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingRecordRepository extends JpaRepository<AccountBillingRecord, Long>,
        JpaSpecificationExecutor<AccountBillingRecord> {

    AccountBillingRecord findOneByUid(String uid);

    List<AccountBillingRecord> findByUidIn(List<String> uids);

    AccountBillingRecord findOneByPaymentId(String paymentId);

    AccountBillingRecord findTopByAccountOrderByCreatedDateTimeDesc(Account account);

    int countByAccountAndPaidTrue(Account account);

}
