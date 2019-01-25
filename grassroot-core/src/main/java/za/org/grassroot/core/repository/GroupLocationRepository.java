package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.group.Group;

import java.time.LocalDate;
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
}
