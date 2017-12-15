package za.org.grassroot.services.group;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipDTO;
import za.org.grassroot.services.ChangedSinceData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Created by luke on 2016/09/26.
 */
public interface GroupQueryBroker {

    Group load(String groupUid);

    boolean groupExists(String groupUid);

    List<Group> loadAll();

    List<GroupSearchResultDTO> groupSearch(String userUid, String searchTerm, boolean searchPublic);

    List<Group> searchUsersGroups(String userUid, String searchTerm, boolean onlyCreatedGroups);

    ChangedSinceData<Group> getActiveGroups(User user, Instant changedSince);

    /**
     * Core search method. Finds discoverable groups corresponding to the term given, for which the user is not a member.
     * If location filter is null, then no location filtering is performed.
     *
     * @param userUid user searcher
     * @param searchTerm query string
     * @param locationFilter optional, nullable, location filter options
     * @param restrictToGroupName restricts to just the group name, e.g., if want to display separately
     * @return group list
     */
    List<Group> findPublicGroups(String userUid, String searchTerm, GroupLocationFilter locationFilter, boolean restrictToGroupName);

    Optional<Group> findGroupFromJoinCode(String joinCode);

    /** METHODS FOR DEALING WITH SUBGROUPS, LINKING GROUPS, AND MERGING **/

    Set<Group> mergeCandidates(String userUid, String groupUid);

    Set<Group> subGroups(String groupUid);

    Set<Group> possibleParents(String userUid, String groupUid);

    LocalDateTime getLastTimeGroupActiveOrModified(String groupUid);

    GroupLog getMostRecentLog(Group group);

    List<LocalDate> getMonthsGroupActive(String groupUid);

    List<GroupLog> getLogsForGroup(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    List<Group> fetchGroupsWithOneCharNames(User creatingUser, int sizeThreshold);

    Page<Group> fetchUserCreatedGroups(User user, int pageNumber, int pageSize);

    /** TO CHECK IF GROUP IS PAID OR NOT **/

    boolean isGroupPaidFor(String groupUid);

    long countMembershipsInGroups(User groupCreator, Instant groupCreatedAfter, Instant userJoinedAfter);

    Page<MembershipDTO> getMembershipsInGroups(User groupCreator, Instant groupCreatedAfter, Instant userJoinedAfter, Pageable pageable);

}
