package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserCreateRequest;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface UserManagementService {

    /*
    Methods to create and load a specific user
     */

    User load(String userUid);

    User createUserProfile(User userProfile);

    User deleteAndroidUserProfile(User user) throws NoSuchProfileException;

    UserDTO loadUserCreateRequest(String phoneNumber);

    User createUserWebProfile(User userProfile) throws UserExistsException;

    User createAndroidUserProfile(UserDTO userDTO) throws UserExistsException;

    User updateUserAndroidProfileSettings(User user,String name, String language, AlertPreference alertPreference);

    String generateAndroidUserVerifier(String phoneNumber, String displayName);

    boolean userExist(String phoneNumber);

    void setInitiatedSession(User sessionUser);

    boolean isPartOfActiveGroups(User user);

    User save(User userToSave);

    User loadOrSaveUser(String inputNumber);

    User findByInputNumber(String inputNumber) throws NoSuchUserException;

    User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException;

    User fetchUserByUsername(String username);

    Group fetchGroupUserMustRename(User user);

    /*
    Methods to return lists of users
     */

    Set<User> fetchByGroup(String groupUid, boolean includeSubgroups);

    List<User> searchByGroupAndNameNumber(String groupUid, String nameOrNumber);

    Page<User> getGroupMembers(Group group, int pageNumber, int pageSize);

    /*
    Methods to set and retrieve varfious properties about a user
     */

    boolean needsToRenameSelf(User sessionUser);

    boolean needsToRSVP(User sessionUser);

    boolean needsToVote(User sessionUser);

    boolean needsToVoteOrRSVP(User sessionUser);

    boolean hasIncompleteLogBooks(String userUid, long daysInPast);
    
    User resetUserPassword(String username, String newPassword, String token);

    String getLastUssdMenu(String inputNumber);

    User setLastUssdMenu(User sessionUser, String lastUssdMenu);

    User setDisplayName(User user, String displayName);

    User setUserLanguage(User sessionUser, String locale);



    LinkedHashMap<String, String> getImplementedLanguages();

}
