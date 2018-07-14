package za.org.grassroot.core.specifications;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role_;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.User_;
import za.org.grassroot.core.domain.group.*;
import za.org.grassroot.core.enums.Province;

import javax.persistence.criteria.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Slf4j
public class MembershipSpecifications {


    private static Specification<Membership> membershipsCreatedAfter(Instant joinAfter) {
        return (root, query, cb) -> cb.greaterThan(root.get(Membership_.joinTime), joinAfter);
    }

    private static Specification<Membership> membershipsOfGroupsCreatedAfter(Instant groupCreatedAfter) {
        return (root, query, cb) -> cb.greaterThan(root.get(Membership_.group).get(Group_.createdDateTime), groupCreatedAfter);
    }

    private static Specification<Membership> membershipsOfGroupsCreatedBy(User groupCreator) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.group).get(Group_.createdByUser), groupCreator);
    }

    public static Specification<Membership> membershipsInGroups(User groupCreator, Instant groupCreatedAfter, Instant membershipCreatedAfter) {
        Specification<Membership> groupCreatedByUserSpec = MembershipSpecifications.membershipsOfGroupsCreatedBy(groupCreator);
        Specification<Membership> groupCreatedAfterSpec = MembershipSpecifications.membershipsOfGroupsCreatedAfter(groupCreatedAfter);
        Specification<Membership> membershipCreatedAfterSpec = MembershipSpecifications.membershipsCreatedAfter(membershipCreatedAfter);
        return Specification.where(groupCreatedByUserSpec).and(groupCreatedAfterSpec).and(membershipCreatedAfterSpec);
    }

    public static Specification<Membership> recentMembershipsInGroups(List<Group> groups, Instant membershipCreatedAfter, User callingUser) {
        Specification<Membership> inGroups = MembershipSpecifications.forGroups(groups);
        Specification<Membership> membershipCreatedAfterSpec = MembershipSpecifications.membershipsCreatedAfter(membershipCreatedAfter);
        Specification<Membership> notSubGroup = (root, query, cb) -> cb.notEqual(root.get(Membership_.joinMethod),
                GroupJoinMethod.ADDED_SUBGROUP);
        Specification<Membership> notUser = (root, query, cb) -> cb.notEqual(root.get(Membership_.user), callingUser);
        return Specification.where(inGroups).and(membershipCreatedAfterSpec).and(membershipCreatedAfterSpec)
                .and(notSubGroup).and(notUser);
    }

    public static Specification<Membership> filterGroupMembership(Group group,
                                                                  Collection<Province> provinces,
                                                                  Boolean unknownProvince,
                                                                  Collection<String> taskTeamsUids,
                                                                  Collection<GroupJoinMethod> joinMethods,
                                                                  Integer joinDaysAgo,
                                                                  LocalDate joinDate,
                                                                  JoinDateCondition joinDaysAgoCondition,
                                                                  String namePhoneOrEmail,
                                                                  Collection<String> languages){

        return (Root<Membership> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {

            List<Predicate> restrictions = new ArrayList<>();

            if (provinces != null && provinces.size() > 0) {
                restrictions.add(root.get(Membership_.user).get(User_.province).in(provinces));
            }

            if (unknownProvince != null && unknownProvince) {
                restrictions.add(root.get(Membership_.user).get(User_.province).isNull());
            }

            if (taskTeamsUids != null && taskTeamsUids.size() > 0) {
                restrictions.add(root.get(Membership_.group).get(Group_.uid).in(taskTeamsUids));
            } else {
                restrictions.add(cb.equal(root.get(Membership_.group), group));
            }

            if (joinMethods != null && !joinMethods.isEmpty()) {
                restrictions.add(root.get(Membership_.joinMethod).in(joinMethods));
            }

            LocalDate queriedJoinDate = joinDate;
            if(joinDate == null && joinDaysAgo != null){
                queriedJoinDate = LocalDate.now().minusDays(joinDaysAgo);
            }

            if (queriedJoinDate != null) {
                Instant joinDateInstant = queriedJoinDate.atStartOfDay().toInstant(ZoneOffset.UTC);
                switch (joinDaysAgoCondition){
                    case EXACT :
                        restrictions.add( cb.between(root.get(Membership_.joinTime), joinDateInstant, joinDateInstant.plus(1, ChronoUnit.DAYS)));
                        break;
                    case BEFORE:
                        restrictions.add( cb.lessThanOrEqualTo(root.get(Membership_.joinTime), joinDateInstant.plus(1, ChronoUnit.DAYS)));
                        break;
                    case AFTER:
                        restrictions.add( cb.greaterThanOrEqualTo(root.get(Membership_.joinTime), joinDateInstant));
                        break;
                }

            }

            if (!StringUtils.isEmpty(namePhoneOrEmail)) {
                log.info("adding predicates for name, phone, email: ", namePhoneOrEmail);
                List<Predicate> nameSearchPredicates = new ArrayList<>();
                Arrays.stream(namePhoneOrEmail.split(","))
                        .map(term -> term.trim().toLowerCase())
                        .filter(term -> !StringUtils.isEmpty(term))
                        .forEach(term -> {
                            Predicate byName = cb.like(cb.lower(root.get(Membership_.user).get(User_.displayName)),
                                    "%" + term + "%");
                            Predicate byPhone = cb.like(cb.lower(root.get(Membership_.user).get(User_.phoneNumber)),
                                    "%" + term + "%");
                            Predicate byEmail = cb.like(cb.lower(root.get(Membership_.user).get(User_.emailAddress)),
                                    "%" + term + "%");
                            nameSearchPredicates.add(cb.or(byName, byEmail, byPhone));
                        });
                restrictions.add(cb.or(nameSearchPredicates.toArray(new Predicate[0])));
            }

            if (languages != null && languages.size() > 0) {
                restrictions.add(root.get(Membership_.user).get(User_.languageCode).in(languages));
            }

            log.info("have generated {} predicates", restrictions.size());
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

    public static Specification<Membership> groupOrganizers(Group group) {
        return Specification.where(hasRole(BaseRoles.ROLE_GROUP_ORGANIZER)).and(forGroup(group));
    }

    public static Specification<Membership> membersJoinedBefore(Instant time) {
        return (root, query, cb) -> cb.lessThan(root.get(Membership_.joinTime), time);
    }

    public static Specification<Membership> inSubgroupOf(Group group) {
        return (root, query, cb) -> {
            Join<Membership, Group> join = root.join(Membership_.group, JoinType.INNER);
            return cb.equal(join.get(Group_.parent), group);
        };
    }

    public static Specification<Membership> hasUser(User user) {
        return (root, query, cb) -> cb.equal(root.get(Membership_.user), user);
    }

    public static Specification<Membership> memberTaskTeams(Group group, User user) {
        return Specification.where(inSubgroupOf(group)).and(hasUser(user));
    }

}
