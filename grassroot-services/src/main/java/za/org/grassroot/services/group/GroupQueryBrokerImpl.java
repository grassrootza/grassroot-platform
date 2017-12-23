package za.org.grassroot.services.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.dto.MembershipDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.core.specifications.PaidGroupSpecifications;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.util.FullTextSearchUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static za.org.grassroot.core.specifications.GroupSpecifications.hasParent;
import static za.org.grassroot.core.specifications.GroupSpecifications.isActive;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by luke on 2016/09/28.
 */
@Service
public class GroupQueryBrokerImpl implements GroupQueryBroker {

    private static final Logger logger = LoggerFactory.getLogger(GroupQueryBrokerImpl.class);

    private static final int DEFAULT_RADIUS = 10000; // 10 km
    
    private static final float LOCATION_FILTER_SCORE_MIN = 0.2f;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    @Autowired
    private GroupLocationRepository groupLocationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private MembershipRepository membershipRepository;

    @Override
    @Transactional(readOnly = true)
    public Group load(String groupUid) {
        return groupRepository.findOneByUid(groupUid);
    }

    @Override
    public boolean groupExists(String groupUid) {
        return groupRepository.findOneByUid(groupUid) != null;
    }

    @Override
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<Group> loadAll() {
        return groupRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupSearchResultDTO> groupSearch(String userUid, String searchTerm, boolean searchPublic) {
        List<GroupSearchResultDTO> results = new ArrayList<>();
        if (!searchPublic) {
            searchUsersGroups(userUid, searchTerm, true)
                    .forEach(g -> results.add(new GroupSearchResultDTO(g, GroupResultType.USER_MEMBER)));
        } else {
            findPublicGroups(userUid, searchTerm, null, true)
                    .forEach(g -> results.add(new GroupSearchResultDTO(g, GroupResultType.PUBLIC_BY_NAME)));
        }
        return results;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Group> searchUsersGroups(String userUid, String searchTerm, boolean onlyCreatedGroups) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(searchTerm);

        if (searchTerm.trim().isEmpty()) {
            throw new IllegalArgumentException("Error, cannot search for blank term");
        }

        User user = userRepository.findOneByUid(userUid);
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, true, true);
        logger.info("Searching term: {}", tsQuery);

        return groupRepository.findByActiveAndMembershipsUserWithNameContainsText(user.getId(), tsQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> findPublicGroups(String userUid, String searchTerm, GroupLocationFilter locationFilter, boolean restrictToGroupName) {
        Objects.requireNonNull(userUid);

        logger.info("Finding public groups: userUid={}, searchTerm={}, locationFilter={}", userUid, searchTerm, locationFilter);

        User user = userRepository.findOneByUid(userUid);
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchTerm, true, false);
        List<Group> groups = restrictToGroupName ? groupRepository.findDiscoverableGroupsWithNameWithoutMember(user.getId(), tsQuery) :
                groupRepository.findDiscoverableGroupsWithNameOrTaskTextWithoutMember(user.getId(), tsQuery);

        Predicate<Group> locationPredicate = constructLocationPredicate(user, new HashSet<>(groups), locationFilter);
        return groups.stream()
                .filter(Group::isActive)
                .filter(locationPredicate)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Group> findGroupFromJoinCode(String joinCode) {
        Group groupToReturn = groupRepository.findOne(GroupSpecifications.hasJoinCode(joinCode));
        if (groupToReturn == null) return Optional.empty();
        if (groupToReturn.getTokenExpiryDateTime().isBefore(Instant.now())) return null;
        return Optional.of(groupToReturn);
    }

    @Override
    public Set<Group> subGroups(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return new HashSet<>(groupRepository.findAll(Specifications.where(hasParent(group)).and(isActive())));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Group> possibleParents(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group groupToMakeChild = groupRepository.findOneByUid(groupUid);

        Set<Group> groupsWithPermission = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_CREATE_SUBGROUP);
        groupsWithPermission.remove(groupToMakeChild);

        return groupsWithPermission.stream().filter(g -> !isGroupAlsoParent(groupToMakeChild, g)).collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Group> mergeCandidates(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // todo: may want to check for both update and add members ...
        Set<Group> otherGroups = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        otherGroups.remove(group);
        return otherGroups;
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDateTime getLastTimeGroupActiveOrModified(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        Event latestEvent = eventRepository.findTopByParentGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
        GroupLog latestGroupLog = groupLogRepository.findFirstByGroupOrderByCreatedDateTimeDesc(group);

        LocalDateTime lastActive = (latestEvent != null) ? latestEvent.getEventDateTimeAtSAST() : LocalDateTime.ofInstant(group.getCreatedDateTime(), getSAST());
        LocalDateTime lastModified = (latestGroupLog != null) ? LocalDateTime.ofInstant(latestGroupLog.getCreatedDateTime(), getSAST()) :
                LocalDateTime.ofInstant(group.getCreatedDateTime(), getSAST());

        return (lastActive != null && lastActive.isAfter(lastModified)) ? lastActive : lastModified;
    }

    @Override
    @Transactional(readOnly = true)
    public ChangedSinceData<Group> getActiveGroups(User user, Instant changedSince) {
        Objects.requireNonNull(user, "User cannot be null");
        List<Group> activeGroups = groupRepository.findByMembershipsUserAndActiveTrueAndParentIsNull(user);
        // here we put all those groups that have been satisfying query above, but not anymore since 'changedSince' moment
        Set<String> removedUids = new HashSet<>();
        if (changedSince != null) {
            List<Group> deactivatedAfter = groupRepository.findMemberGroupsDeactivatedAfter(user, changedSince);
            List<Group> formerMembersGroups = groupRepository.findMembershipRemovedAfter(user, changedSince);
            removedUids = Stream.concat(deactivatedAfter.stream(), formerMembersGroups.stream())
                    .map(Group::getUid)
                    .collect(Collectors.toSet());
        }
        List<Group> groups = activeGroups.stream()
                .filter(group -> changedSince == null || isGroupChangedSince(group, changedSince))
                .collect(Collectors.toList());
        return new ChangedSinceData<>(groups, removedUids);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupLog getMostRecentLog(Group group) {
        return groupLogRepository.findFirstByGroupOrderByCreatedDateTimeDesc(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalDate> getMonthsGroupActive(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        LocalDate groupStartDate = LocalDateTime.ofInstant(group.getCreatedDateTime(), getSAST()).toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate monthIterator = LocalDate.of(groupStartDate.getYear(), groupStartDate.getMonth(), 1);
        List<LocalDate> months = new ArrayList<>();
        while (monthIterator.isBefore(today)) {
            months.add(monthIterator);
            monthIterator = monthIterator.plusMonths(1L);
        }
        return months;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupLog> getLogsForGroup(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Sort sort = new Sort(Sort.Direction.DESC, "CreatedDateTime");
        return groupLogRepository.findByGroupAndCreatedDateTimeBetween(group, convertToSystemTime(periodStart, getSAST()),
                convertToSystemTime(periodEnd, getSAST()), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> fetchGroupsWithOneCharNames(User user, int sizeThreshold) {
        //for now limiting this to only groups created by the user
        List<Group> candidateGroups = new ArrayList<>(groupRepository.findActiveGroupsWithNamesLessThanOneCharacter(user));
        return candidateGroups.stream()
                .filter(group -> group.getMembers().size() <= sizeThreshold)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Group> fetchUserCreatedGroups(User user, int pageNumber, int pageSize) {
        Objects.requireNonNull(user);
        return groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user, new PageRequest(pageNumber, pageSize));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isGroupPaidFor(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return paidGroupRepository.count(Specifications
                .where(PaidGroupSpecifications.isForGroup(group))
                .and(PaidGroupSpecifications.expiresAfter(Instant.now()))) > 0;
    }

    /*
    Auxiliary methods below
     */

    private boolean isGroupChangedSince(Group group, Instant changedSince) {
        GroupLog mostRecentLog = getMostRecentLog(group);
        if (mostRecentLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }

        Event mostRecentEvent = eventRepository.findTopByParentGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
        if (mostRecentEvent != null) {
            if (mostRecentEvent.getCreatedDateTime().isAfter(changedSince)) {
                return true;
            }

            // if most recent event is created before last time user checked this group, then we check if this event has been changed after this last time
            EventLog lastChangeEventLog = eventLogRepository.findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(mostRecentEvent, EventLogType.CHANGE);
            if (lastChangeEventLog != null && lastChangeEventLog.getCreatedDateTime().isAfter(changedSince)) {
                return true;
            }
        }

        return false;
    }

    /*
    Values
     */

    private List<Group> parentChain(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        List<Group> parentGroups = new ArrayList<Group>();
        recursiveParentGroups(group, parentGroups);
        return parentGroups;
    }

    // todo: watch & verify this method
    private void recursiveParentGroups(Group childGroup, List<Group> parentGroups) {
        parentGroups.add(childGroup);
        if (childGroup.getParent() != null && childGroup.getParent().getId() != 0) {
            recursiveParentGroups(childGroup.getParent(),parentGroups);
        }
    }

    // if this returns true, then the group being passed as child is already in the parent chain of the desired
    // parent, which will create an infinite loop, hence prevent it
    private boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup) {
        for (Group g : parentChain(possibleParentGroup.getUid())) {
            if (g.getId().equals(possibleChildGroup.getId())) return true;
        }
        return false;
    }

    private Predicate<Group> constructLocationPredicate(User user, Set<Group> groups, GroupLocationFilter locationFilter) {
        if (locationFilter == null) {
            return group -> true; // always included predicate

        } else {
            LocalDate localDate = LocalDate.now();
            GeoLocation center = resolveAreaCenter(locationFilter.getCenter(), user, localDate);

            if (center == null) {
                return group -> true; // always included predicate if user's location is not found (center is null)

            } else {
                int radius = resolveRadius(locationFilter.getRadius());
                List<GroupLocation> groupLocations = groupLocationRepository.findByGroupInAndLocalDateAndScoreGreaterThan(groups, localDate, LOCATION_FILTER_SCORE_MIN);
                // useful map for quick search of group location; used within following predicate
                Map<Group, GeoLocation> groupGeoLocations = groupLocations.stream()
                        .collect(Collectors.toMap(GroupLocation::getGroup, GroupLocation::getLocation));

                return group -> {
                    GeoLocation geoLocation = groupGeoLocations.get(group);
                    if (geoLocation == null) {
                        return locationFilter.isIncludeWithoutLocation();

                    } else {
                        // is group location within radius ?
                        int distanceFromCenter = geoLocation.calculateDistanceInMetersFrom(center);
                        return distanceFromCenter < radius;
                    }
                };
            }
        }
    }

    private GeoLocation resolveAreaCenter(GeoLocation centerDefinition, User user, LocalDate localDate) {
        // if there is no center specified, we try to take user's current location
        if (centerDefinition == null) {
            PreviousPeriodUserLocation previousPeriodUserLocation = geoLocationBroker.fetchUserLocation(user.getUid(), localDate);
            if (previousPeriodUserLocation == null) {
                return null;
            }
            return previousPeriodUserLocation.getLocation();

        } else {
            return centerDefinition;
        }
    }

    private int resolveRadius(Integer radiusDefinition) {
        if (radiusDefinition == null) {
            return DEFAULT_RADIUS;
        } else {
            return radiusDefinition;
        }
    }


    @Override
    public long countMembershipsInGroups(User groupCreator, Instant groupCreatedAfter, Instant userJoinedAfter) {
        return membershipRepository.count(MembershipSpecifications.membershipsInGroups(groupCreator, groupCreatedAfter, userJoinedAfter));
    }

    @Override
    public Page<MembershipDTO> getMembershipsInGroups(User groupCreator, Instant groupCreatedAfter, Instant userJoinedAfter, Pageable pageable) {
        Page<Membership> page = membershipRepository.findAll(MembershipSpecifications.membershipsInGroups(groupCreator, groupCreatedAfter, userJoinedAfter), pageable);
        return page.map(MembershipDTO::new);

    }


}
