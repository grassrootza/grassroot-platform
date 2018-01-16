package za.org.grassroot.services.group;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipFullDTO;
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

    GroupFullDTO fetchGroupFullDetails(String userUid, String groupUid);

    List<MembershipRecordDTO> fetchRecentMembershipChanges(String userUid, String groupUid, Instant fromDate);

    List<GroupWebDTO> fetchGroupWebInfo(String userUid);

    Page<MembershipFullDTO> fetchGroupMembers(User user, String groupUid, Pageable pageable);

    MembershipFullDTO fetchGroupMember(String userUid, String groupUid, String memberUid);

    Page<Membership> fetchUserGroupsNewMembers(User user, Instant from, Pageable pageable);

    List<ActionLog> fetchUserActivityDetails(String queryingUserUid, String groupUid, String memberUid);

}
