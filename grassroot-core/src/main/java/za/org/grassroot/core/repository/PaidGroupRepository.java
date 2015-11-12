package za.org.grassroot.core.repository;

/**
 * Created by luke on 2015/07/16.
 */
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;

public interface PaidGroupRepository extends JpaRepository<PaidGroup, Long> {


}
