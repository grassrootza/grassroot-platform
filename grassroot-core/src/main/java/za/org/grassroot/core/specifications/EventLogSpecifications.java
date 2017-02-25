package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.EventLog_;
import za.org.grassroot.core.enums.EventLogType;

/**
 * Created by luke on 2017/02/25.
 */
public final class EventLogSpecifications {

    public static Specification<EventLog> forEvent(Event e) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.event), e);
    }

    public static Specification<EventLog> ofType(EventLogType type) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.eventLogType), type);
    }

}
