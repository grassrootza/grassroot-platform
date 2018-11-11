package za.org.grassroot.services.group;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.services.ChangedSinceData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Created by luke on 2016/09/26.
 */
public interface GroupQueryBroker {

    Group load(String groupUid);

    List<Group> loadAll();

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

    GroupLog getMostRecentLog(Group group);

    Page<Group> fetchUserCreatedGroups(User user, int pageNumber, int pageSize);

}
