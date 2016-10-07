package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.PaidGroup;

import java.time.Instant;
import java.util.List;

public interface PaidGroupRepository extends JpaRepository<PaidGroup, Long> {

    PaidGroup findOneByUid(String paidGroupUid);

    List<PaidGroup> findByExpireDateTimeGreaterThan(Instant date);

    List<PaidGroup> findByAccount(Account account);

    PaidGroup findTopByGroupOrderByExpireDateTimeDesc(Group group);

}
