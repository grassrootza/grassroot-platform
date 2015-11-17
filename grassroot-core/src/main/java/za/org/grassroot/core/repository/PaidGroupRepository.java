package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;

import java.util.Date;
import java.util.List;

public interface PaidGroupRepository extends JpaRepository<PaidGroup, Long> {

    List<PaidGroup> findByExpireDateTimeGreaterThan(Date date);

    List<PaidGroup> findByAccount(Account account);

}
