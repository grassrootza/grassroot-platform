package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface GroupJoinCodeRepository extends JpaRepository<GroupJoinCode, UUID> {

    @Query("select lower(gjc.code) from GroupJoinCode gjc where gjc.active = true")
    Set<String> selectLowerCaseActiveJoinWords();

    @Query("select gjc.group from GroupJoinCode  gjc where gjc.active = true and lower(gjc.code) = lower(?1)")
    Optional<Group> selectGroupWithActiveCode(String code);

    List<GroupJoinCode> findByActiveTrue();
}
