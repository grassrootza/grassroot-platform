package za.org.grassroot.services.user;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;

import java.util.List;
import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
public interface UserManagementService {

    /*
    Methods to load a specific user
     */

    User load(String userUid);

    User loadOrCreateUser(String inputNumber); // used only in USSD where there is no registration process

    User findByInputNumber(String inputNumber) throws NoSuchUserException;

    User findByNumberOrEmail(String inputNumber, String emailAddress);

    User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException;

    // if can't find by username itself, tries phone number or email
    User findByUsernameLoose(String userName);

    // only checks the username property alone
    User fetchUserByUsernameStrict(String username);

    boolean userExist(String phoneNumber);

    List<User> searchByGroupAndNameNumber(String groupUid, String nameOrNumber);

    // username can be msisdn or pwd
    boolean doesUserHaveStandardRole(String userName, String roleName);

    UserProfileStatus fetchUserProfileStatus(String userUid);

    /*
    Methods to create a user, for various interfaces
     */

    String create(String phoneNumber, String displayName, String emailAddress);

    User createUserProfile(User userProfile);

    UserDTO loadUserCreateRequest(String phoneNumber);

    User createUserWebProfile(User userProfile) throws UserExistsException;

    User createAndroidUserProfile(UserDTO userDTO) throws UserExistsException;

    String generateAndroidUserVerifier(String phoneNumber, String displayName, String password);

    String regenerateUserVerifier(String phoneNumber, boolean createUserIfNotExists);

    /*
    Methods to update user properties
     */

    // note: returns "false" if an OTP is needed to complete this but is not present,
    // throws an invalid error if an OTP is provided but is not valid
    // returns true otherwise
    boolean updateUser(String userUid, String displayName, String phoneNumber,
                       String emailAddress, Province province, AlertPreference alertPreference,
                       Locale locale, String validationOtp);

    void updateDisplayName(String callingUserUid, String userToUpdateUid, String displayName);

    void setDisplayNameByOther(String updatingUserUid, String targetUserUid, String displayName);

    void updateUserLanguage(String userUid, Locale locale);

    void updateAlertPreferences(String userUid, AlertPreference alertPreference);

    void setMessagingPreference(String userUid, DeliveryRoute preference);

    void setHasInitiatedUssdSession(String userUid);

    void resetUserPassword(String username, String newPassword, String token);

    void updateEmailAddress(String callingUserUid, String userUid, String emailAddress);

    void updatePhoneNumber(String callingUserUid, String userUid, String phoneNumber);

    /*
    Miscellaneous methods to query various properties about a user
     */

    boolean needsToRenameSelf(User sessionUser);

    void sendAndroidLinkSms(String userUid);

    List<String[]> findOthersInGraph(User user, String nameFragment);

    List<User> findRelatedUsers(User user, String nameFragment);

}
