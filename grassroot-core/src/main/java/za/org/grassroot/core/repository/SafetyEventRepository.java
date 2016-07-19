package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.SafetyEvent;

import java.util.List;

/**
 * Created by paballo on 2016/07/18.
 */
public interface SafetyEventRepository extends JpaRepository<SafetyEvent, Long> {

    SafetyEvent findOneByUid(String uid);

    List<SafetyEvent> findByGroup(Group group);
}
