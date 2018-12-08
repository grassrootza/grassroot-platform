package za.org.grassroot.services.user;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.dto.UserMinimalProjection;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface UserManagementService {

    User load(String userUid);

    List<User> load(Set<String> userUids);

    User loadOrCreateUser(String msisdn, UserInterfaceType channel); // used only in USSD where there is no registration process

    User loadOrCreate(String phoneOrEmail);

    User findByInputNumber(String inputNumber) throws NoSuchUserException;

    UserMinimalProjection findUserMinimalByMsisdn(String msisdn) throws NoSuchUserException;

    UserMinimalProjection findUserMinimalAndStashMenu(String msisdn, String currentUssdMenu) throws NoSuchUserException;

    User findByNumberOrEmail(String inputNumber, String emailAddress);

    User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException;

    // if can't find by username itself, tries phone number or email
    User findByUsernameLoose(String userName);

    // only checks the username property alone
    User fetchUserByUsernameStrict(String username);

    boolean userExist(String phoneNumber);

    boolean emailTaken(String userUid, String email);

    // username can be msisdn or pwd
    boolean doesUserHaveStandardRole(String userName, String roleName);

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
                       Locale locale, String validationOtp,boolean whatsappOptIn, UserInterfaceType channel);

    UserMinimalProjection updateDisplayName(String callingUserUid, String userToUpdateUid, String displayName);

    UserMinimalProjection updateUserLanguage(String userUid, Locale locale, UserInterfaceType channel);

    UserMinimalProjection updateUserProvince(String userUid, Province province);

    void updateAlertPreferences(String userUid, AlertPreference alertPreference);

    void setMessagingPreference(String userUid, DeliveryRoute preference);

    void setHasInitiatedUssdSession(String userUid, boolean sendWelcomeMessage);

    void resetUserPassword(String username, String newPassword, String token);

    void updateEmailAddress(String callingUserUid, String userUid, String emailAddress);

    void updatePhoneNumber(String callingUserUid, String userUid, String phoneNumber);

    void updateHasImage(String userUid, boolean hasImage);

    void updateContactError(String userUid, boolean hasContactError);

    void deleteUser(String userUid, String validationOtp);

    /*
    Miscellaneous methods to query various properties about a user
     */

    boolean needsToSetName(User user, boolean evenOnCreation);

    boolean needsToSetProvince(User user, boolean evenOnCreation);

    boolean needToPromptForLanguage(User sessionUser, int minSessions);

    boolean shouldSendLanguageText(User sessionUser);

    void sendAndroidLinkSms(String userUid);

    List<String[]> findOthersInGraph(User user, String nameFragment);

    List<User> findRelatedUsers(User user, String nameFragment);

    UserRegPossibility checkUserCanRegister(String phone, String email);

    List<User> findUsersThatRsvpForEvent(Event event, EventRSVPResponse response);

    List<User> findUsersNotifiedAboutEvent(Event event, Class<? extends EventNotification> notificationClass);

    void saveUserLocation(String userUid, GeoLocation geoLocation,UserInterfaceType userInterfaceType);

}
