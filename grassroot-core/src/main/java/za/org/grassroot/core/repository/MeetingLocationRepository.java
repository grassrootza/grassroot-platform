package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.MeetingLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import java.util.Collection;
import java.util.List;

public interface MeetingLocationRepository extends JpaRepository<MeetingLocation, Long> {

    // note : subequery makes this difficult to do with specifications, hence this way
    // note : reconsider keeping old calculation results if subquery performance starts degrading
    @Query("select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
        + " m.uid"
        + ",m.name"
        + ",l.location.latitude"
        + ",l.location.longitude"
        + ",l.score"
        + ",'MEETING' "
        + ",m.description"
        + ",m.isPublic"
        + ")"
        + " from MeetingLocation l"
        + " inner join l.meeting m"
        + " where m.isPublic = true and m.ancestorGroup in :groups and l.calculatedDateTime <= :date"
        + " and l.calculatedDateTime = (select max(ll.calculatedDateTime) from MeetingLocation ll where ll.meeting = l.meeting)")
    List<ObjectLocation> findAllLocationsWithDateAfterAndGroupIn(Collection<Group> groups);

}
