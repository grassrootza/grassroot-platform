package za.org.grassroot.services.group;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipDTO;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.dto.group.GroupWebDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupFetchBroker {

    Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged);

    Set<GroupMinimalDTO> fetchGroupMinimalInfo(String userUid, Set<String> groupUids);

    List<GroupMinimalDTO> fetchAllUserGroupsSortByLatestTime(String userUid);

    Set<GroupFullDTO> fetchGroupFullInfo(String userUid, Set<String> groupUids);

    GroupFullDTO fetchGroupFullDetails(String userUid, String groupUid);

    Set<MembershipDTO> fetchGroupMembershipInfo(String userUid, String groupUid);

    Page<MembershipFullDTO> fetchGroupMembers(User user, String groupUid, Pageable pageable);

    public List<GroupWebDTO> fetchGroupWebInfo(String userUid);

}
