package za.org.grassroot.services.group;


import za.org.grassroot.core.dto.group.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupFetchBroker {

    Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged);

    Set<GroupMinimalDTO> fetchGroupMinimalInfo(String userUid, Set<String> groupUids);

    List<GroupMinimalDTO> fetchAllUserGroupsSortByLatestTime(String userUid);

    GroupFullDTO fetchGroupFullInfo(String userUid, String groupUid);

    List<MembershipRecordDTO> fetchRecentMembershipChanges(String userUid, String groupUid, Instant fromDate);

    public List<GroupWebDTO> fetchGroupWebInfo(String userUid);

}
