package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.SafetyEventLog;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/07/19.
 */
public interface SafetyEventLogRepository extends JpaRepository<SafetyEventLog, Long> {

    SafetyEventLog findOneByUid(String uid);

    SafetyEventLog findByUserAndSafetyEvent(User user, SafetyEvent safetyEvent);



}
