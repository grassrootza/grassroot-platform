package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.Province;

import javax.persistence.criteria.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MembershipSpecifications {


    public static Specification<Membership> membershipsCreatedAfter(Instant joinAfter) {
        return (root, query, cb) -> cb.greaterThan(root.get(Membership_.joinTime), joinAfter);
    }

    public static Specification<Membership> membershipsOfGroupsCreatedAfter(Instant groupCreatedAfter) {
        return (root, query, cb) -> cb.greaterThan(root.get(Membership_.group).get(Group_.createdDateTime), groupCreatedAfter);
    }

    public static Specification<Membership> membershipsOfGroupsCreatedBy(User groupCreator) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.group).get(Group_.createdByUser), groupCreator);
    }


    public static Specifications<Membership> membershipsInGroups(User groupCreator, Instant groupCreatedAfter, Instant membershipCreatedAfter) {
        Specification<Membership> groupCreatedByUserSpec = MembershipSpecifications.membershipsOfGroupsCreatedBy(groupCreator);
        Specification<Membership> groupCreatedAfterSpec = MembershipSpecifications.membershipsOfGroupsCreatedAfter(groupCreatedAfter);
        Specification<Membership> membershipCreatedAfterSpec = MembershipSpecifications.membershipsCreatedAfter(membershipCreatedAfter);
        return Specifications.where(groupCreatedByUserSpec).and(groupCreatedAfterSpec).and(membershipCreatedAfterSpec);
    }

    public static Specifications<Membership> recentMembershipsInGroups(List<Group> groups, Instant membershipCreatedAfter) {
        Specification<Membership> inGroups = MembershipSpecifications.forGroups(groups);
        Specification<Membership> membershipCreatedAfterSpec = MembershipSpecifications.membershipsCreatedAfter(membershipCreatedAfter);
        return Specifications.where(inGroups).and(membershipCreatedAfterSpec).and(membershipCreatedAfterSpec);
    }

    public static Specifications<Membership> groupMembersInProvincesJoinedAfter(Group group,
                                                                                Collection<Province> provinces,
                                                                                Instant joinedDate) {
        return Specifications.where(forGroup(group)).and(memberJoinedAfter(joinedDate))
                .and(memberInProvinces(provinces));
    }




    public static Specification<Membership> filterGroupMembership(Group group,
                                                                  Collection<Province> provinces,
                                                                  Collection<String> taskTeamsUids,
                                                                  Collection<GroupJoinMethod> joinMethods,
                                                                  Collection<String> joinedViaCampaignUids ){

        return (root, query, cb) -> {

            List<Predicate> restrictions = new ArrayList<>();


            if (provinces != null && provinces.size() > 0) {
                restrictions.add(root.get(Membership_.user).get(User_.province).in(provinces));
            }

            if (taskTeamsUids != null && taskTeamsUids.size() > 0) {
                restrictions.add(root.get(Membership_.group).get(Group_.uid).in(taskTeamsUids));
            } else {
                restrictions.add(cb.equal(root.get(Membership_.group), group));
            }

            if (joinMethods != null) {
                restrictions.add(root.get(Membership_.joinMethod).in(joinMethods));
            }

            return cb.and(restrictions.toArray(new Predicate[0]));
        };

    }

    private static Specification<Membership> hasRole(String roleName) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.role).get(Role_.name), roleName);
    }

    private static Specification<Membership> forGroups(List<Group> groups) {
        return (root, query, cb) -> root.get(Membership_.group).in(groups);
    }

    public static Specification<Membership> forGroup(Group group) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.group), group);
    }

    public static Specification<Membership> forGroup(String groupUid) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.group).get(Group_.uid), groupUid);
    }

    public static Specifications<Membership> groupOrganizers(Group group) {
        return Specifications.where(hasRole(BaseRoles.ROLE_GROUP_ORGANIZER)).and(forGroup(group));
    }

    private static Specification<Membership> memberInProvinces(Collection<Province> provinces) {
        return (root, query, cb) -> {
            Join<Membership, User> userJoin = root.join(Membership_.user, JoinType.INNER);
            return userJoin.get(User_.province).in(provinces);
        };
    }

    private static Specification<Membership> memberJoinedAfter(Instant cutOffDateTime) {
        return (root, query, cb) -> cb.greaterThan(root.get(Membership_.joinTime), cutOffDateTime);
    }

    public static Specification<Membership> membersJoinedBefore(Instant time) {
        return (root, query, cb) -> cb.lessThan(root.get(Membership_.joinTime), time);
    }


}
