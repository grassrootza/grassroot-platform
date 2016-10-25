package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;

import java.util.List;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingRecordRepository extends JpaRepository<AccountBillingRecord, Long> {

    List<AccountBillingRecord> findByAccountOrderByCreatedDateTimeDesc(Account account);

}
