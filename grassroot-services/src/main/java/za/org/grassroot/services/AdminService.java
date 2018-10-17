package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.membership.MembershipInfo;

import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/02/04.
 */
public interface AdminService {

    void updateGroupActive(String adminUserUid, String groupUid, boolean active);

    void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo);

    void removeUserFromAllGroups(String adminUserUid, String userUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void addSystemRole(String adminUserUid, String userUid, String systemRole);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void removeStdRole(String adminUserUid, String userUid, String systemRole);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    String createUserWithSystemRole(String adminUserUid, String displayName, String phoneNumber,
                                  String emailAddress, String systemRole);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<User> getUsersWithStdRole(String adminUserUid, String systemRole);

    //@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateUserPassword(String adminUserUid, String userUid, String newPassword);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    long sendBatchOfAndroidLinks(String adminUserUid, int batchSize);

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
