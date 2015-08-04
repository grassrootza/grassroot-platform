package za.org.grassroot.meeting_organizer.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.meeting_organizer.domain.Event;

public interface EventRepository extends CrudRepository<Event, Integer> {
}
