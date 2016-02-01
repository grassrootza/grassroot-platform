package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2016/02/01.
 */
public interface GroupLogService {

    public GroupLog load(Long groupLogId);

    public List<GroupLog> getLogsForGroup(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

}
