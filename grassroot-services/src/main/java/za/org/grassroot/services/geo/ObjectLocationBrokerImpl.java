package za.org.grassroot.services.geo;

import org.hibernate.jpa.internal.QueryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.repository.GroupLocationRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MeetingLocationRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.group.GroupLocationFilter;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObjectLocationBrokerImpl implements ObjectLocationBroker {

    private static final Logger logger = LoggerFactory.getLogger(ObjectLocationBroker.class);

    private final EntityManager entityManager;
    private final GroupRepository groupRepository;
    private final GroupLocationRepository groupLocationRepository;
    private final MeetingLocationRepository meetingLocationRepository;

    @Autowired
    public ObjectLocationBrokerImpl(EntityManager entityManager, GroupRepository groupRepository, GroupLocationRepository groupLocationRepository, MeetingLocationRepository meetingLocationRepository) {
        this.entityManager = entityManager;
        this.groupRepository = groupRepository;
        this.groupLocationRepository = groupLocationRepository;
        this.meetingLocationRepository = meetingLocationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius) {

        // TODO: 1) Use the user restrictions and search for public groups
        // TODO: 2) Use the radius to search
        List<ObjectLocation> list = entityManager.createQuery(
                "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " g.uid"
                        + ",g.groupName"
                        + ",l.location.latitude"
                        + ",l.location.longitude"
                        + ",l.score"
                        + ",'GROUP'"
                        + ",g.description"
                        + ")"
                        + " from GroupLocation l"
                        + " inner join l.group g"
                        + " where g.discoverable = true and l.localDate <= :date and"
                        + " l.localDate = (select max(ll.localDate) from GroupLocation ll where ll.group = l.group)",
                ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }


    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchLocationsWithFilter(GroupLocationFilter filter) {
        List<ObjectLocation> locations = new ArrayList<>();

        Instant earliestDate = filter.getMinimumGroupLifeWeeks() == null ? DateTimeUtil.getEarliestInstant() :
                LocalDate.now().minus(filter.getMinimumGroupLifeWeeks(), ChronoUnit.WEEKS)
                .atStartOfDay().toInstant(ZoneOffset.UTC);

        // note : if need to optimize performance, leave out size counts if nulls passed, instead of check > 0
        List<Group> groupsToInclude = entityManager.createQuery("" +
                "select g from Group g where " +
                "g.createdDateTime >= :createdDateTime and " +
                "size(g.memberships) >= :minMembership and " +
                "(size(g.descendantEvents) + size(g.descendantTodos)) >= :minTasks", Group.class)
                .setParameter("createdDateTime", earliestDate)
                .setParameter("minMembership", filter.getMinimumGroupSize() == null ? 0 : filter.getMinimumGroupSize())
                .setParameter("minTasks", filter.getMinimumGroupTasks() == null ? 0 : filter.getMinimumGroupTasks())
                .getResultList();

        locations.addAll(groupLocationRepository.findAllLocationsWithDateAfterAndGroupIn(groupsToInclude));
        locations.addAll(meetingLocationRepository.findAllLocationsWithDateAfterAndGroupIn(groupsToInclude));

        return locations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchMeetingLocations(GeoLocation geoLocation, Integer radius) {
        // TODO: 1) Use the user restrictions and search for public groups/meetings
        // TODO: 2) Use the radius to search
        List<ObjectLocation> list = entityManager.createQuery(
                    "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " m.uid"
                        + ",m.name"
                        + ",l.location.latitude"
                        + ",l.location.longitude"
                        + ",l.score"
                        + ",'MEETING'"
                        + ")"
                        + " from MeetingLocation l"
                        + " inner join l.meeting m"
                        + " where m.isPublic = true and l.calculatedDateTime <= :date"
                        + " and l.calculatedDateTime = (select max(ll.calculatedDateTime) from MeetingLocation ll where ll.meeting = l.meeting)",
                   ObjectLocation.class
                )
                .setParameter("date", Instant.now())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    @Override
    public List<ObjectLocation> fetchMeetingLocationsByGroup(ObjectLocation group, GeoLocation geoLocation, Integer radius) {
        // TODO: 1) Use the user restrictions and search for public groups/meetings
        // TODO: 2) Use the radius to search
        List<ObjectLocation> list = entityManager.createQuery(
                "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " m.uid"
                        + ",m.name"
                        + ",l.location.latitude"
                        + ",l.location.longitude"
                        + ",l.score"
                        + ",'MEETING'"
                        + ")"
                        + " from Meeting m"
                        + " inner join m.parentGroup g"
                        + ",GroupLocation l"
                        + " where l.localDate <= :date"
                        + " and l.group = g"
                        + " and g.uid = :guid"
                        + " and l.localDate = (select max(ll.localDate) from GroupLocation ll where ll.group = l.group)",
                    ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
                .setParameter("guid", group.getUid())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }
}
