package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    Account findOneByUid(String accountUid);

    Set<Account> findByUidIn(Set<String> uids);

    List<Account> findByAccountName(String accountName);

    List<Account> findByDisabledDateTimeAfter(Instant time);

    Account findByAdministrators(User administrator);

    Account findByPaidGroups(PaidGroup paidGroup);

}
