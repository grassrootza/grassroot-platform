package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.WelcomeNotification;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.UserRequestRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */

@Service
@Transactional
public class UserManager implements UserManagementService, UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserManager.class);

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private PasswordTokenService passwordTokenService;
    @Autowired
    private EventManagementService eventManagementService;
    @Autowired
    private CacheUtilService cacheUtilService;
    @Autowired
    private AsyncUserLogger asyncUserService;
    @Autowired
    private UserRequestRepository userCreateRequestRepository;
    @Autowired
    private LogBookRepository logBookRepository;
    @Autowired
    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    @Autowired
    private MessageAssemblingService messageAssemblingService;
    @Autowired
    private GcmService gcmService;
    

    @Override
    public User load(String userUid) {
        return userRepository.findOneByUid(userUid);
    }

    @Override
    public User createUserProfile(User userProfile) {
        return userRepository.save(userProfile);
    }

    @Override
    @Transactional
    public User createUserWebProfile(User userProfile) throws UserExistsException {

        Assert.notNull(userProfile, "User is required");
        Assert.hasText(userProfile.getPhoneNumber(), "User phone number is required");

        User userToSave;
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());
        boolean userExists = userExist(phoneNumber);

        if (userExists) {

            log.info("The user exists, and their web profile is set to: " + userProfile.isHasWebProfile());

            User userToUpdate = findByInputNumber(phoneNumber);
            if (userToUpdate.isHasWebProfile()) {
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a web profile!");
            }

            if (!userToUpdate.hasName()) {
                userToUpdate.setDisplayName(userProfile.getDisplayName());
            }

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setHasWebProfile(true);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {
            userToSave = new User(phoneNumber, userProfile.getDisplayName());
            userToSave.setUsername(phoneNumber);
            userToSave.setHasWebProfile(true);
        }

        if (passwordEncoder != null) {
            userToSave.setPassword(passwordEncoder.encode(userProfile.getPassword()));
        } else {
            log.warn("PasswordEncoder not set, skipping password encryption...");
        }

        try {
            User userToReturn = userRepository.saveAndFlush(userToSave);
            if (!userExists)
                asyncUserService.recordUserLog(userToReturn.getUid(), UserLogType.CREATED_IN_DB, "Web");
            asyncUserService.recordUserLog(userToReturn.getUid(), UserLogType.CREATED_WEB, "User created web profile");
            return userToReturn;
        } catch (final Exception e) {
            throw new UserExistsException("User '" + userProfile.getUsername() + "' already exists!");
        }

    }

    @Override
    public User createAndroidUserProfile(UserDTO userDTO) throws UserExistsException {
        Objects.requireNonNull(userDTO);
        User userProfile = new User(userDTO.getPhoneNumber(), userDTO.getDisplayName());
        User userToSave;
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());
        boolean userExists = userExist(phoneNumber);

        if (userExists) {

            User userToUpdate = loadOrSaveUser(phoneNumber);
            if (userToUpdate.hasAndroidProfile() && userToUpdate.getMessagingPreference().equals(UserMessagingPreference.ANDROID_APP)) {
                log.warn("User already has android profile");
                throw new UserExistsException("User '"  + userProfile.getUsername() + "' already has a android profile!");
            }

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setHasAndroidProfile(true);
            userToUpdate.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
            userToUpdate.setAlertPreference(AlertPreference.NOTIFY_ALL_EVENTS);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {

            userProfile.setPhoneNumber(phoneNumber);
            userProfile.setUsername(phoneNumber);
            userProfile.setDisplayName(userDTO.getDisplayName());
            userProfile.setHasAndroidProfile(true);
            userProfile.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
            userProfile.setAlertPreference(AlertPreference.NOTIFY_ALL_EVENTS);
            userToSave = userProfile;
        }

        try {
            User userToReturn = userRepository.saveAndFlush(userToSave);
            if (!userExists)
                asyncUserService.recordUserLog(userToReturn.getUid(), UserLogType.CREATED_IN_DB, "Android");
            asyncUserService.recordUserLog(userToReturn.getUid(), UserLogType.REGISTERED_ANDROID, "User created android profile");
            return userToReturn;
        } catch (final Exception e) {
            e.printStackTrace();
            log.warn(e.getMessage());
            throw new UserExistsException("User '" + userProfile.getUsername() + "' already exists!");
        }

    }

    @Override
    public User deleteAndroidUserProfile(User user)  {

        if (!user.hasAndroidProfile()) {
            throw  new NoSuchProfileException();
        }
            user.setHasAndroidProfile(false);
            user.setMessagingPreference(UserMessagingPreference.SMS);
            user.setHasInitiatedSession(true);

            try {
                user = userRepository.saveAndFlush(user);
                gcmService.unregisterUser(user);
                asyncUserService.recordUserLog(user.getUid(), UserLogType.DEREGISTERED_ANDROID, "User android profile deleted");
            } catch (final Exception e) {
                e.printStackTrace();
                log.warn(e.getMessage());
            }
            return user;

    }

    @Override
    public User updateUserAndroidProfileSettings(User user, String name, String language, AlertPreference alertPreference)
            throws IllegalArgumentException {
        Objects.nonNull(user);
        Objects.nonNull(name);
        Objects.nonNull(language);
        Objects.nonNull(alertPreference);
        user.setDisplayName(name);
        user.setLanguageCode(language);
        user.setAlertPreference(alertPreference);

        return userRepository.saveAndFlush(user);
    }

    @Override
    public String generateAndroidUserVerifier(String phoneNumber, String displayName) {
        Objects.nonNull(phoneNumber);
        phoneNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        if (displayName != null) {
            UserCreateRequest userCreateRequest = userCreateRequestRepository.findByPhoneNumber(phoneNumber);
            if (userCreateRequest == null) {
                userCreateRequest = new UserCreateRequest(phoneNumber, displayName, Instant.now());
            } else {
                userCreateRequest.setDisplayName(displayName);
                userCreateRequest.setCreationTime(Instant.now());
            }
            userCreateRequestRepository.save(userCreateRequest);
        }
        VerificationTokenCode token = passwordTokenService.generateAndroidVerificationCode(phoneNumber);
        return token.getCode();
    }

    @Override
    @Transactional(readOnly = true)
    public void setMessagingPreference(String userUid, UserMessagingPreference preference) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(preference);

        User user = userRepository.findOneByUid(userUid);
        user.setMessagingPreference(preference);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        if (StringUtils.isEmpty(username)) {
            throw new UsernameNotFoundException("Username not found.");
        }

        User user = userRepository.findByUsername(username.toLowerCase().trim());
        if (user == null) {
            throw new UsernameNotFoundException("Username not found.");
        }

        // let's initialize standard roles and membership hibernate collection this way
        user.getAuthorities();

        return user;
    }

    @Override
    public User save(User userToSave) {
        return userRepository.save(userToSave);
    }

    /**
     * Creating some functions to internalize conversion of phone numbers and querying
     */

    @Override
    public User loadOrSaveUser(String inputNumber) {
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(inputNumber);
        if (!userExist(phoneNumber)) {
            User sessionUser = new User(phoneNumber);
            sessionUser.setUsername(phoneNumber);
            User newUser = userRepository.save(sessionUser);
            asyncUserService.recordUserLog(newUser.getUid(), UserLogType.CREATED_IN_DB, "Created via loadOrSaveUser");
            return newUser;
        } else {
            return userRepository.findByPhoneNumber(phoneNumber);
        }
    }

    /**
     * Methods to keep track also of where a user is in the ussd menu flow, so can return them to that spot if time out
     */

    private void saveUssdMenu(User user, String menuToSave) {
        log.info("USSD menu passed to cache: " + menuToSave);
        cacheUtilService.putUssdMenuForUser(user.getPhoneNumber(), menuToSave);
    }

    /*
    Method to load or save a user and store that they have initiated the session. Putting it in services and making it
    distinct from standard loadOrSaveUser because we may want to optimize it aggressively in future.
     */

    @Override
    public User findByInputNumber(String inputNumber) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        if (sessionUser == null) throw new NoSuchUserException("Could not find user with phone number: " + inputNumber);
        return sessionUser;
    }

    @Override
    public User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException {

        User sessionUser = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        cacheUtilService.putUssdMenuForUser(inputNumber, currentUssdMenu);

        return sessionUser;
    }

    @Override
    public List<User> searchByGroupAndNameNumber(String groupUid, String nameOrNumber) {
        return userRepository.findByGroupsPartOfAndDisplayNameContainingIgnoreCaseOrPhoneNumberLike(
                groupRepository.findOneByUid(groupUid), "%" + nameOrNumber + "%", "%" + nameOrNumber + "%");
    }

    @Override
    public Page<User> getGroupMembers(Group group, int pageNumber, int pageSize) {
        return userRepository.findByGroupsPartOf(group, new PageRequest(pageNumber,pageSize));
    }

    @Override
    public boolean userExist(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public boolean isPartOfActiveGroups(User user) {
        return (groupRepository.countByMembershipsUserAndActiveTrue(user) > 0);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean needsToRenameSelf(User user) {
        return !user.hasName() && (!asyncUserService.hasSkippedName(user.getUid())
                && user.getCreatedDateTime().toInstant().isBefore(Instant.now().minus(3, ChronoUnit.MINUTES)));
    }

    @Override
    public boolean needsToRSVP(User sessionUser) {
        // todo: as noted elsewhere, probably want to optimize this quite aggressively
        return eventManagementService.getOutstandingRSVPForUser(sessionUser).size() > 0;
    }

    @Override
    public boolean needsToVote(User sessionUser) {
        log.info("Checking if vote outstanding for user: " + sessionUser);
        return eventManagementService.getOutstandingVotesForUser(sessionUser).size() > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasIncompleteLogBooks(String userUid, long daysInPast) {
        // checks for incomplete entries
        User user = userRepository.findOneByUid(userUid);
        Instant start = Instant.now().minus(daysInPast, ChronoUnit.DAYS);
        Instant end = Instant.now();
        return (logBookRepository.countByParentGroupMembershipsUserAndActionByDateBetweenAndCompleted(user, start, end, false) > 0);
    }

    /*
    Method for user to reset password themselves, relies on them being able to access a token
     */

    @Override
    public User resetUserPassword(String username, String newPassword, String token) {

        log.info("Inside reset user password ...");

        User user = userRepository.findByUsername(PhoneNumberUtil.convertPhoneNumber(username));

        log.info("Found this user: " + user);

        if (passwordTokenService.isVerificationCodeValid(user, token)) {
            String encodedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(encodedPassword);
            user = userRepository.save(user);
        }
        return user;
    }

    @Override
    public User fetchUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public void setInitiatedSession(User sessionUser) {
        sessionUser.setHasInitiatedSession(true);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        UserLog userLog = new UserLog(sessionUser.getUid(), UserLogType.INITIATED_USSD, "First USSD active session", UserInterfaceType.UNKNOWN);
        bundle.addLog(userLog);

        String[] welcomeMessageIds = new String[] {
                "sms.welcome.1",
                "sms.welcome.2",
                "sms.welcome.3"
        };
        for (String welcomeMessageId : welcomeMessageIds) {
            String message = messageAssemblingService.createWelcomeMessage(welcomeMessageId, sessionUser);
            WelcomeNotification notification = new WelcomeNotification(sessionUser, message, userLog);
            // notification sending delay of 2days
            notification.setNextAttemptTime(Instant.now().plus(48, ChronoUnit.HOURS));
            bundle.addNotification(notification);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    public Group fetchGroupUserMustRename(User user) {
        Group lastCreatedGroup = groupRepository.findFirstByCreatedByUserOrderByIdDesc(user);
        if (lastCreatedGroup != null && lastCreatedGroup.isActive() && !lastCreatedGroup.hasName()
                && !asyncUserService.hasSkippedNamingGroup(user.getUid(), lastCreatedGroup.getUid()))
            return lastCreatedGroup;
        else
            return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<User> fetchByGroup(String groupUid, boolean includeSubgroups) {
        Group group = groupRepository.findOneByUid(groupUid);
        if (includeSubgroups) {
            return group.getMembersWithChildrenIncluded();
        } else {
            return group.getMembers();
        }
    }

    @Override
    public String getLastUssdMenu(String inputNumber) {
        return cacheUtilService.fetchUssdMenuForUser(inputNumber);
    }

    @Override
    public User setLastUssdMenu(User sessionUser, String lastUssdMenu) {
        cacheUtilService.putUssdMenuForUser(sessionUser.getPhoneNumber(), lastUssdMenu);
        return sessionUser;
    }

    @Override
    public User setDisplayName(User user, String displayName) {
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    @Override
    public User setUserLanguage(User sessionUser, String locale) {
        sessionUser.setLanguageCode(locale);
        cacheUtilService.putUserLanguage(sessionUser.getPhoneNumber(), locale);
        return userRepository.save(sessionUser);
    }

    @Override
    public LinkedHashMap<String, String> getImplementedLanguages() {

        // todo: replace calls to one in USSDController to this one

        LinkedHashMap<String, String> languages = new LinkedHashMap<>();

        languages.put("en", "English");
        languages.put("nso", "Sepedi");
        languages.put("st", "Sesotho");
        languages.put("ts", "Tsonga");
        languages.put("zu", "Zulu");

        return languages;
    }

    /*
    SECTION: methods to return a masked user entity, for analytics
     */

    @Override
    public UserDTO loadUserCreateRequest(String phoneNumber) {
        UserCreateRequest userCreateRequest = userCreateRequestRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(phoneNumber));
        return (new UserDTO(userCreateRequest));
    }
}
