package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MaskedUserDTO;
import za.org.grassroot.core.dto.MembershipInfo;

import java.util.List;

/**
 * Created by luke on 2016/02/04.
 */
public interface AdminService {

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<MaskedUserDTO> searchByInputNumberOrDisplayName(String inputNumber);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateGroupActive(String adminUserUid, String groupUid, boolean active);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void removeMemberFromGroup(String adminUserUid, String groupUid, String memberMsisdn);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
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

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateUserPassword(String adminUserUid, String userUid, String newPassword);

}
