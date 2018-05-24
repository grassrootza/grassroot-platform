package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Event_;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.EventLog_;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventLogType;

import javax.persistence.criteria.Join;

/**
 * Created by luke on 2017/02/25.
 */
public final class EventLogSpecifications {

    public static Specification<EventLog> forEvent(Event e) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.event), e);
    }

    public static Specification<EventLog> forUser(User u) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.user), u);
    }

    public static Specification<EventLog> forGroup(Group group) {
        return (root, query, cb) -> {
            Join<EventLog, Event> join = root.join(EventLog_.event);
            return cb.equal(join.get(Event_.ancestorGroup), group);
        };
    }

    public static Specification<EventLog> ofType(EventLogType type) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.eventLogType), type);
    }

    public static Specification<EventLog> isImageLog() {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get(EventLog_.eventLogType), EventLogType.IMAGE_RECORDED),
                cb.equal(root.get(EventLog_.eventLogType), EventLogType.IMAGE_AT_CREATION));
    }

    public static Specification<EventLog> isImageLogWithKey(String key) {
        return (root, query, cb) -> cb.or(
                cb.and(cb.equal(root.get(EventLog_.uid), key),
                        cb.equal(root.get(EventLog_.eventLogType), EventLogType.IMAGE_RECORDED)),
                cb.and(cb.equal(root.get(EventLog_.tag), key),
                        cb.equal(root.get(EventLog_.eventLogType), EventLogType.IMAGE_AT_CREATION)));
    }

    public static Specification<EventLog> hasLocation() {
        return (root, query, cb) -> cb.isNotNull(root.get(EventLog_.location));
    }

    public static Specification<EventLog> isResponseToVote(Vote vote) {
        return (root, query, cb) -> cb.and(cb.equal(root.get(EventLog_.event), vote),
                cb.equal(root.get(EventLog_.eventLogType), EventLogType.VOTE_OPTION_RESPONSE));
    }

    public static Specification<EventLog> isResponseToAnEvent() {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get(EventLog_.eventLogType), EventLogType.VOTE_OPTION_RESPONSE),
                cb.equal(root.get(EventLog_.eventLogType), EventLogType.RSVP));
    }

    public static Specification<EventLog> hasTag(String tag) {
        return (root, query, cb) -> cb.equal(root.get(EventLog_.tag), tag);
    }

}
