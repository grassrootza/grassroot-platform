package za.org.grassroot.services.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.util.FullTextSearchUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private GroupLocationRepository groupLocationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventLogRepository eventLogRepository;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @Override
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<Group> loadAll() {
        return groupRepository.findAll();
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

        try {
            return groupRepository.findByActiveAndMembershipsUserWithNameContainsText(user.getId(), tsQuery);
        } catch (JpaSystemException e) {
            logger.error("Curious empty result set error (non-replicable locally) thrown, returning empty list");
            return new ArrayList<>();
        }
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

        logger.info("Groups found {}",groups.size());
        Predicate<Group> locationPredicate = constructLocationPredicate(user, new HashSet<>(groups), locationFilter);
        return groups.stream()
                .filter(Group::isActive)
                .filter(locationPredicate)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Group> findGroupFromJoinCode(String joinCode) {
        Optional<Group> groupToReturn = groupRepository.findOne(GroupSpecifications.hasJoinCode(joinCode));
        if (!groupToReturn.isPresent() || groupToReturn.get().getTokenExpiryDateTime().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return groupToReturn;
    }

    @Override
    @Transactional(readOnly = true)
    public ChangedSinceData<Group> getActiveGroups(User user, Instant changedSince) {
        Objects.requireNonNull(user, "User cannot be null");
        logger.info("checking for user changed since: {}", changedSince);
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
    public Page<Group> fetchUserCreatedGroups(User user, int pageNumber, int pageSize) {
        Objects.requireNonNull(user);
        return groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user,
                PageRequest.of(pageNumber, pageSize));
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


}
