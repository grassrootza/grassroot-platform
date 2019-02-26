package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.membership.MembershipInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by luke on 2016/02/04.
 */
public interface AdminService {

    void updateGroupActive(String adminUserUid, String groupUid, boolean active);

    void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo);

    void removeUserFromAllGroups(String adminUserUid, String userUid);

    Optional<Group> findGroupByJoinCode(String adminUserUid, String joinCode);

    void removeJoinCodeFromGroup(String adminUserUid, String groupUid);

    void grantJoinCodeToGroup(String adminUserUid, String groupUid, String joinCode);

    //@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateUserPassword(String adminUserUid, String userUid, String newPassword);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateConfigVariable(String key, String newValue,String description);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void createConfigVariable(String key, String newValue,String description);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    Map<String, String> getCurrentConfigVariables();

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void deleteConfigVariable(String key);

    int freeUpInactiveJoinTokens();

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<ConfigVariable> getAllConfigVariables();

}
