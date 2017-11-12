package za.org.grassroot.core.specifications;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.domain.Specifications;
import za.org.grassroot.core.domain.*;

import java.time.Instant;

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

    private static Specification<Membership> hasRole(String roleName) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.role).get(Role_.name), roleName);
    }

    private static Specification<Membership> forGroup(Group group) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.group), group);
    }

    public static Specifications<Membership> groupOrganizers(Group group) {
        return Specifications.where(hasRole(BaseRoles.ROLE_GROUP_ORGANIZER)).and(forGroup(group));
    }
}
