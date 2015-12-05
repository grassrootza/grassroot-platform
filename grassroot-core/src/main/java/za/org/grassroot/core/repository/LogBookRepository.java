package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.LogBook;

import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookRepository extends JpaRepository<LogBook, Long> {

    List<LogBook> findAllByGroupId(Long groupId);
    List<LogBook> findAllByGroupIdAndCompleted(Long groupId, boolean completed);
    List<LogBook> findAllByAssignedToUserId(Long assignToUserId);
    List<LogBook> findAllByAssignedToUserIdAndCompleted(Long assignToUserId, boolean completed);
    List<LogBook> findAllByReplicatedGroupId(Long replicatedGroupId);
    List<LogBook> findAllByReplicatedGroupIdAndCompleted(Long replicatedGroupId, boolean completed);

}
