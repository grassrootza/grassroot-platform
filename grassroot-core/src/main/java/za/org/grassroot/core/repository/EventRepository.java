package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Event;

public interface EventRepository extends CrudRepository<Event, Long> {
}
