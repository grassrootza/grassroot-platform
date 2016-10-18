package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.domain.User;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Account findOneByUid(String accountUid);

    List<Account> findByAccountName(String accountName);

    List<Account> findByEnabled(boolean enabled);

    Account findByAdministrators(User administrator);

    Account findByPaidGroups(PaidGroup paidGroup);

}
