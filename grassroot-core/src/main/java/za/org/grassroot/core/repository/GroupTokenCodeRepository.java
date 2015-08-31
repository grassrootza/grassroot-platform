package za.org.grassroot.core.repository;

import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupTokenCode;

import java.util.List;

/**
 * Created by luke on 2015/08/30.
 */
public interface GroupTokenCodeRepository extends CrudRepository<GroupTokenCode, Long> {

    List<GroupTokenCode> findByGroup(Group relevantGroup);

    GroupTokenCode findByCode(String code);

}
