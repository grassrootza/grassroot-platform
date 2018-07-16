package za.org.grassroot.core.specifications;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.GroupLog_;
import za.org.grassroot.core.domain.group.Group_;
import za.org.grassroot.core.enums.GroupLogType;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    public static Specification<GroupLog> memberChangeRecords(Group group, Instant startDate) {
        return Specification.where(forGroup(group))
                .and(ofTypes(GroupLogType.targetUserChangeTypes))
                .and(afterDate(startDate));
    }

    public static Specification<GroupLog> containingUser(User user) {
        return (root, query, cb) -> cb.or(cb.equal(root.get(GroupLog_.user), user),
                cb.equal(root.get(GroupLog_.targetUser), user));
    }

    public static Specification<GroupLog> memberCountChanges(String groupUid, Instant startDate, Instant endDate) {
        return Specification.where(forGroup(groupUid))
                .and(ofTypes(GroupLogType.targetUserAddedOrRemovedTypes))
                .and(between(startDate, endDate));
    }

    public static Specification<GroupLog> forInboundMessages(Group group, Instant from, Instant to, String keyword) {
        return (Root<GroupLog> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            List<Predicate> restrictions = new ArrayList<>();

            if(from != null && to != null) {
                restrictions.add( cb.between(root.get(GroupLog_.createdDateTime), from, to.plus(1, ChronoUnit.DAYS)));
            }

            if(!StringUtils.isEmpty(keyword)) {
                restrictions.add(cb.like(cb.lower(root.get(GroupLog_.description)), '%' + keyword.toLowerCase() + '%'));
            }
            restrictions.add(cb.equal(root.get(GroupLog_.groupLogType), GroupLogType.USER_SENT_UNKNOWN_RESPONSE));

            restrictions.add(cb.equal(root.get(GroupLog_.group).get(Group_.uid), group.getUid()));

            return cb.and(restrictions.toArray(new Predicate[0]));
        };
    }


}
