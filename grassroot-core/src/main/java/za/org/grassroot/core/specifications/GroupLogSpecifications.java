package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;

import za.org.grassroot.core.domain.*;

import za.org.grassroot.core.enums.GroupLogType;

import java.time.Instant;
import java.util.Collection;

public class GroupLogSpecifications {



    private static Specification<GroupLog> forGroup(Group group) {
        return (root, query, cb) -> cb.equal(root.get(GroupLog_.group), group);
    }

    private static Specification<GroupLog> forGroup(String groupUid) {
        return (root, query, cb) -> cb.equal(root.get(GroupLog_.group).get(Group_.uid), groupUid);
    }

    private static Specification<GroupLog> ofTypes(Collection<GroupLogType> groupLogTypes) {
        return (root, query, cb) -> root.get(GroupLog_.groupLogType).in(groupLogTypes);
    }

    private static Specification<GroupLog> afterDate(Instant startDate) {
        return (root, query, cb) -> cb.greaterThan(root.get(GroupLog_.createdDateTime), startDate);
    }


    private static Specification<GroupLog> between(Instant startDateTime, Instant endDateTime) {
        return (root, query, cb) -> cb.between(root.get(GroupLog_.createdDateTime), startDateTime, endDateTime);
    }

    public static Specifications<GroupLog> memberChangeRecords(Group group, Instant startDate) {
        return Specifications.where(forGroup(group))
                .and(ofTypes(GroupLogType.targetUserChangeTypes))
                .and(afterDate(startDate));
    }

    public static Specification<GroupLog> containingUser(User user) {
        return (root, query, cb) -> cb.or(cb.equal(root.get(GroupLog_.user), user),
                cb.equal(root.get(GroupLog_.targetUser), user));
    }

    public static Specifications<GroupLog> memberCountChanges(String groupUid, Instant startDate, Instant endDate) {
        return Specifications.where(forGroup(groupUid))
                .and(ofTypes(GroupLogType.targetUserAddedOrRemovedTypes))
                .and(between(startDate, endDate));
    }

}
