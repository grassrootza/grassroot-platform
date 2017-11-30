package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface GroupLocationRepository extends JpaRepository<GroupLocation, Long> {
	void deleteByGroupAndLocalDate(Group group, LocalDate localDate);

	GroupLocation findOneByGroupAndLocalDate(Group group, LocalDate localDate);

	int countByGroupAndLocalDateGreaterThan(Group group, LocalDate localDate);

	List<GroupLocation> findByGroupInAndLocalDateAndScoreGreaterThan(Set<Group> groups, LocalDate localDate, float score);

	@Query("select distinct gl.group from GroupLocation gl ")
	List<Group> findAllGroupsWithLocationData();

	@Query("select distinct g from GroupLocation gl inner join gl.group g where g IN ?1")
	List<Group> findAllGroupsWithLocationDataInReferenceSet(Set<Group> groups);

	// note : subequery makes this difficult to do with specifications, hence this way
	// note : reconsider keeping old calculation results if subquery performance starts degrading
	@Query("select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
			+ " g.uid"
			+ ",g.groupName"
			+ ",l.location.latitude"
			+ ",l.location.longitude"
			+ ",l.score"
			+ ",'GROUP'"
			+ ",g.description"
			+ ",false"
			+ ")"
			+ " from GroupLocation l"
			+ " inner join l.group g"
			+ " where g in :groups and g.discoverable = true and "
			+ " l.localDate = (select max(ll.localDate) from GroupLocation ll where ll.group = l.group)")
	List<ObjectLocation> findAllLocationsWithDateAfterAndGroupIn(Collection<Group> groups);

	@Query(value = "SELECT NEW za.org.grassroot.core.domain.geo.GroupLocation( g.group" +
			",g.localDate" +
			",g.location" +
			",g.score" +
			",g.source" +
			")" +
			" FROM GroupLocation g" +
			" WHERE g.group IN (SELECT m.group FROM Membership m" +
			"                   WHERE m.user =:user" +
            "                   ORDER BY m.joinTime DESC)")
	GroupLocation findByUserUid(@Param("user") User user);

}
