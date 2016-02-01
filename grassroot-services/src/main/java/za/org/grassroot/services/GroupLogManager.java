package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.repository.GroupLogRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2016/02/01.
 */
@Component
public class GroupLogManager implements GroupLogService {

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Override
    public GroupLog load(Long groupLogId) {
        return groupLogRepository.findOne(groupLogId);
    }

    @Override
    public List<GroupLog> getLogsForGroup(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Sort sort = new Sort(Sort.Direction.ASC, "CreatedDateTime");
        return groupLogRepository.findByGroupIdAndCreatedDateTimeBetween(group.getId(), Timestamp.valueOf(periodStart),
                                                                         Timestamp.valueOf(periodEnd), sort);
    }
}
