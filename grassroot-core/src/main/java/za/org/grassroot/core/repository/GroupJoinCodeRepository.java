package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface GroupJoinCodeRepository extends JpaRepository<GroupJoinCode, UUID> {

    @Query("select lower(gjc.code) from GroupJoinCode gjc where gjc.active = true " +
            "and gjc.type = 'JOIN_WORD'")
    Set<String> selectActiveJoinWords();

    @Query("select lower(gjc.code) from GroupJoinCode gjc where gjc.active = true and gjc.group = ?1")
    Set<String> selectActiveJoinCodesForGroup(Group group);

    @Query("select gjc.group from GroupJoinCode  gjc where gjc.active = true and lower(gjc.code) = lower(?1)")
    Group selectGroupWithActiveCode(String code);

    // need to use the UID property in here else postgres throws casting error
    GroupJoinCode findByGroupUidAndCodeAndActiveTrue(String groupUid, String code);
    List<GroupJoinCode> findByGroupUidAndActiveTrue(String groupUid);

    List<GroupJoinCode> findByActiveTrue();

    long countByGroupUidAndActiveTrue(String groupUid);

}
