package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.EventLog_;
import za.org.grassroot.core.domain.Vote;
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

    public static Specification<EventLog> hasLocation() {
        return (root, query, cb) -> cb.isNotNull(root.get(EventLog_.location));
    }

    public static Specification<EventLog> isResponseToVote(Vote vote) {
        return (root, query, cb) -> cb.and(cb.equal(root.get(EventLog_.event), vote),
                cb.equal(root.get(EventLog_.eventLogType), EventLogType.VOTE_OPTION_RESPONSE));
    }

    public static Specification<EventLog> hasTag(String tag) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.tag), tag);
    }

}
