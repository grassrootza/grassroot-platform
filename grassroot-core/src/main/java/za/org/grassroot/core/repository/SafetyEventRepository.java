package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;

import java.time.Instant;
import java.util.List;

/**
 * Created by paballo on 2016/07/18.
 */
public interface SafetyEventRepository extends JpaRepository<SafetyEvent, Long> {

    SafetyEvent findOneByUid(String uid);

    List<SafetyEvent> findByGroup(Group group);

    Long countByActivatedByAndCreatedDateTimeAfterAndFalseAlarm(User user, Instant from, boolean falseAlarm);

    @Transactional
    @Query(value = "select se from SafetyEvent se " +
            "where se.createdDateTime > ?1 " +
            "and se.scheduledReminderTime < ?2 and se.active = true")
    List<SafetyEvent> findSafetyEvents(Instant from, Instant to);

    long countByCreatedDateTimeBetween(Instant from, Instant to);

}