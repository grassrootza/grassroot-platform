package za.org.grassroot.services.group;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipFullDTO;
import za.org.grassroot.core.dto.group.*;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupFetchBroker {

    Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged);

    Set<GroupMinimalDTO> fetchGroupMinimalInfo(String userUid, Set<String> groupUids);

    List<GroupMinimalDTO> fetchAllUserGroupsSortByLatestTime(String userUid);

    GroupFullDTO fetchGroupFullInfo(String userUid, String groupUid);

    GroupFullDTO fetchGroupFullDetails(String userUid, String groupUid);

    GroupFullDTO fetchSubGroupDetails(String userUid, String parentUid, String taskTeamUid);

    List<MembershipRecordDTO> fetchRecentMembershipChanges(String userUid, String groupUid, Instant fromDate);

    List<GroupWebDTO> fetchGroupWebInfo(String userUid);

    Page<MembershipFullDTO> fetchGroupMembers(User user, String groupUid, Pageable pageable);

    MembershipFullDTO fetchGroupMember(String userUid, String groupUid, String memberUid);

    Page<Membership> fetchUserGroupsNewMembers(User user, Instant from, Pageable pageable);

    Group fetchGroupByGroupUid(String groupUid);

    List<ActionLog> fetchUserActivityDetails(String queryingUserUid, String groupUid, String memberUid);

    List<Membership> filterGroupMembers(User user, String groupUid,
                                        Collection<Province> provinces,
                                        Collection<String> taskTeams,
                                        Collection<String> topics,
                                        Collection<String> affiliations,
                                        Collection<GroupJoinMethod> joinMethods,
                                        Collection<String> joinedCampaignsUids,
                                        Integer joinDaysAgo,
                                        LocalDate joinDate,
                                        JoinDateCondition joinDaysAgoCondition,
                                        String namePhoneOrEmail);

}
