package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.GroupLog_;
import za.org.grassroot.core.enums.GroupLogType;

import java.time.Instant;
import java.util.Collection;

public class GroupLogSpecifications {



    private static Specification<GroupLog> forGroup(Group group) {
        return (root, query, cb) -> cb.equal(root.get(GroupLog_.group), group);
    }

    private static Specification<GroupLog> ofTypes(Collection<GroupLogType> groupLogTypes) {
        return (root, query, cb) -> root.get(GroupLog_.groupLogType).in(groupLogTypes);
    }

    private static Specification<GroupLog> afterDate(Instant startDate) {
        return (root, query, cb) -> cb.greaterThan(root.get(GroupLog_.createdDateTime), startDate);
    }

    public static Specifications<GroupLog> memberChangeRecords(Group group, Instant startDate) {
        return Specifications.where(forGroup(group))
                .and(ofTypes(GroupLogType.targetUserChangeTypes))
                .and(afterDate(startDate));
    }

}
