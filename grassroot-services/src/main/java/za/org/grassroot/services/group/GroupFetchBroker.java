package za.org.grassroot.services.group;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.group.GroupFullDTO;
import za.org.grassroot.core.dto.group.GroupLogDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.dto.group.GroupTimeChangedDTO;
import za.org.grassroot.core.dto.group.GroupWebDTO;
import za.org.grassroot.core.dto.group.MembershipRecordDTO;
import za.org.grassroot.core.dto.membership.MembershipFullDTO;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupFetchBroker {

    Set<GroupTimeChangedDTO> findNewlyChangedGroups(String userUid, Map<String, Long> excludedGroupsByTimeChanged);

    Set<GroupMinimalDTO> fetchGroupNamesUidsOnly(String userUid, Set<String> groupUids);

    GroupFullDTO fetchGroupFullInfo(String userUid, String groupUid,
                                    boolean includeAllMembers,
                                    boolean includeAllSubgroups,
                                    boolean includeMemberHistory);

    GroupFullDTO fetchSubGroupDetails(String userUid, String parentUid, String taskTeamUid);

    List<MembershipRecordDTO> fetchRecentMembershipChanges(String userUid, String groupUid, Instant fromDate);

    List<GroupWebDTO> fetchGroupWebInfo(String userUid, boolean includeSubgroups);

    List<GroupRefDTO> fetchGroupNamesUidsOnly(String userUid);

    Page<Group> fetchGroupFiltered(String userUid, Permission requiredPermission, String searchTerm, Pageable pageable);

    Page<Membership> fetchGroupMembers(User user, String groupUid, Pageable pageable);

    MembershipFullDTO fetchGroupMember(String userUid, String groupUid, String memberUid);

    Page<Membership> fetchUserGroupsNewMembers(User user, Instant from, Pageable pageable);

    Group fetchGroupByGroupUid(String groupUid);

    List<ActionLog> fetchUserActivityDetails(String queryingUserUid, String groupUid, String memberUid);

    Page<GroupLogDTO> getInboundMessageLogs(User user, Group group, Instant from, Instant to, String keyword, Pageable pageable);

    List<GroupLogDTO> getInboundMessagesForExport(User user, Group group, Instant from, Instant to, String keyword);

    List<Membership> filterGroupMembers(User user, String groupUid,
                                        Collection<Province> provinces,
                                        Boolean noProvince, // i.e., unknown
                                        Collection<String> taskTeams,
                                        Collection<String> topics,
                                        Collection<String> affiliations,
                                        Collection<GroupJoinMethod> joinMethods,
                                        Collection<String> joinedCampaignsUids,
                                        Integer joinDaysAgo,
                                        LocalDate joinDate,
                                        JoinDateCondition joinDaysAgoCondition,
                                        String namePhoneOrEmail,
                                        Collection<String> languages);

}
