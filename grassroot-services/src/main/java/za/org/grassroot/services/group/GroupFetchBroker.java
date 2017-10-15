package za.org.grassroot.services.group;


import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupFetchBroker {

    Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged);

    Set<GroupMinimalDTO> fetchGroupMinimalInfo(String userUid, Set<String> groupUids);

    List<GroupMinimalDTO> fetchAllUserGroupsSortByLatestTime(String userUid);

    Set<GroupFullDTO> fetchGroupFullInfo(String userUid, Set<String> groupUids);

}
