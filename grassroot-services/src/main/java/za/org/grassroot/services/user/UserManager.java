package za.org.grassroot.services.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
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
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.UserRequestRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.exception.InvalidTokenException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;
import za.org.grassroot.services.util.CacheUtilService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Collectors;

import static za.org.grassroot.services.specifications.UserSpecifications.inGroups;
import static za.org.grassroot.services.specifications.UserSpecifications.nameContains;

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
    private CacheUtilService cacheUtilService;
    @Autowired
    private AsyncUserLogger asyncUserService;
    @Autowired
    private UserRequestRepository userCreateRequestRepository;
    @Autowired
    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    @Autowired
    private MessageAssemblingService messageAssemblingService;
    @Autowired
    private MessagingServiceBroker messagingServiceBroker;

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Value("${grassroot.welcome.messages.enabled:false}")
    private boolean welcomeMessageEnabled;

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
        long start = System.nanoTime();
        boolean userExists = userExist(phoneNumber);
        long time = System.nanoTime() - start;
        log.info("User exists check took {} nanosecs", time);

        if (userExists) {

            log.info("The user exists, and their web profile is set to: " + userProfile.isHasWebProfile());

            User userToUpdate = findByInputNumber(phoneNumber);
            if (userToUpdate.isHasWebProfile()) {
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a web profile!");
            }

            if (!userToUpdate.hasName()) {
                userToUpdate.setDisplayName(userProfile.getDisplayName());
                userToUpdate.setHasSetOwnName(true);
            }

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setHasWebProfile(true);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {
            userToSave = new User(phoneNumber, userProfile.getDisplayName());
            userToSave.setUsername(phoneNumber);
            userToSave.setHasWebProfile(true);
            userToSave.setHasSetOwnName(true);
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
    @Transactional
    public User createAndroidUserProfile(UserDTO userDTO) throws UserExistsException {
        Objects.requireNonNull(userDTO);
        User userProfile = new User(userDTO.getPhoneNumber(), userDTO.getDisplayName());
        User userToSave;
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());
        boolean userExists = userExist(phoneNumber);

        if (userExists) {

            User userToUpdate = userRepository.findByPhoneNumber(phoneNumber);
            if (userToUpdate.hasAndroidProfile() && userToUpdate.getMessagingPreference().equals(UserMessagingPreference.ANDROID_APP)) {
                log.warn("User already has android profile");
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a android profile!");
            }

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setHasAndroidProfile(true);
            userToUpdate.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
            userToUpdate.setAlertPreference(AlertPreference.NOTIFY_NEW_AND_REMINDERS);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {

            userProfile.setPhoneNumber(phoneNumber);
            userProfile.setUsername(phoneNumber);
            userProfile.setDisplayName(userDTO.getDisplayName());
            userProfile.setHasSetOwnName(true);
            userProfile.setHasAndroidProfile(true);
            userProfile.setMessagingPreference(UserMessagingPreference.ANDROID_APP);
            userProfile.setAlertPreference(AlertPreference.NOTIFY_NEW_AND_REMINDERS);
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
    @Transactional
    public void updateUser(String userUid, String displayName, String emailAddress, AlertPreference alertPreference, Locale locale)
            throws IllegalArgumentException {
        Objects.nonNull(userUid);

        User user = userRepository.findOneByUid(userUid);

        // retain this since the client-side validation is a bit strange & unpredictable on this field
        if (!StringUtils.isEmpty(displayName)) {
            user.setDisplayName(displayName);
            user.setHasSetOwnName(true);
        }

        // note: make sure to confirm with user if deleting address (i.e., second half of if statement)
        if (!StringUtils.isEmpty(emailAddress) || !StringUtils.isEmpty(user.getEmailAddress())) {
            user.setEmailAddress(emailAddress);
        }

        user.setLanguageCode(locale.getLanguage());
        user.setAlertPreference(alertPreference);
        user.setNotificationPriority(alertPreference.getPriority());
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
        VerificationTokenCode token = passwordTokenService.generateShortLivedOTP(phoneNumber);
        return token.getCode();
    }

    @Override
    @Transactional
    public String regenerateUserVerifier(String phoneNumber, boolean createUserIfNotExists) {
        User user = userRepository.findByPhoneNumber(phoneNumber);
        if (user == null) {
            if (createUserIfNotExists) {
                UserCreateRequest userCreateRequest = userCreateRequestRepository.findByPhoneNumber(phoneNumber);
                if (userCreateRequest == null) {
                    throw new AccessDeniedException("Error! Trying to resend OTP for user before creating");
                }
            } else {
                throw new AccessDeniedException("Error! Trying to create an OTP for non-existent user");
            }
        }
        VerificationTokenCode newTokenCode = passwordTokenService.generateShortLivedOTP(phoneNumber);
        return newTokenCode.getCode();
    }

    @Override
    @Transactional
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

    /**
     * Creating some functions to internalize conversion of phone numbers and querying
     */

    @Override
    public User loadOrCreateUser(String inputNumber) {
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(inputNumber);
        if (!userExist(phoneNumber)) {
            User sessionUser = new User(phoneNumber);
            sessionUser.setUsername(phoneNumber);
            User newUser = userRepository.save(sessionUser);
            asyncUserService.recordUserLog(newUser.getUid(), UserLogType.CREATED_IN_DB, "Created via loadOrCreateUser");
            return newUser;
        } else {
            return userRepository.findByPhoneNumber(phoneNumber);
        }
    }

    /*
    Method to load or save a user and store that they have initiated the session. Putting it in services and making it
    distinct from standard loadOrCreateUser because we may want to optimize it aggressively in future.
     */

    @Override
    @Transactional(readOnly = true)
    public User findByInputNumber(String inputNumber) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        if (sessionUser == null) throw new NoSuchUserException("Could not find user with phone number: " + inputNumber);
        return sessionUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        cacheUtilService.putUssdMenuForUser(inputNumber, currentUssdMenu);
        return sessionUser;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> searchByGroupAndNameNumber(String groupUid, String nameOrNumber) {
        return userRepository.findByGroupsPartOfAndDisplayNameContainingIgnoreCaseOrPhoneNumberLike(
                groupRepository.findOneByUid(groupUid), "%" + nameOrNumber + "%", "%" + nameOrNumber + "%");
    }

    @Override
    @Transactional
    public String create(String phoneNumber, String displayName, String emailAddress) {
        Objects.requireNonNull(phoneNumber);
        Objects.requireNonNull(displayName);

        String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        if (userExist(msisdn)) {
            throw new UserExistsException("Error! User with that phone number exists!");
        }

        User user = new User(msisdn);
        user.setUsername(msisdn);
        user.setDisplayName(displayName);
        user.setEmailAddress(emailAddress);
        user = userRepository.save(user);
        asyncUserService.recordUserLog(user.getUid(), UserLogType.CREATED_IN_DB, "Created via sponsorship request");
        return user.getUid();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userExist(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendAndroidLinkSms(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        String message = messageAssemblingService.createAndroidLinkSms(user);
        messagingServiceBroker.sendSMS(message, user.getPhoneNumber(), true);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean needsToRenameSelf(User user) {
        return !user.hasName() && (!asyncUserService.hasSkippedName(user.getUid())
                && user.getCreatedDateTime().isBefore(Instant.now().minus(3, ChronoUnit.MINUTES)));
    }

    /*
    Method for user to reset password themselves, relies on them being able to access a token
     */

    @Override
    public User resetUserPassword(String username, String newPassword, String token) {

        User user = userRepository.findByUsername(PhoneNumberUtil.convertPhoneNumber(username));
        log.info("Found this user: " + user);

        if (passwordTokenService.isShortLivedOtpValid(user.getPhoneNumber(), token.trim())) {
            log.info("came in as true, with this token :" + token);
            String encodedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(encodedPassword);
            user = userRepository.save(user);
            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);
            return user;
        } else {
            throw new InvalidTokenException("Invalid OTP submitted");
        }
    }

    @Override
    @Transactional
    public void updateEmailAddress(String userUid, String emailAddress) {
        User user = userRepository.findOneByUid(userUid);
        user.setEmailAddress(emailAddress);
    }

    @Override
    public User fetchUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public void setHasInitiatedUssdSession(String userUid) {
        User sessionUser = userRepository.findOneByUid(userUid);

        sessionUser.setHasInitiatedSession(true);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        UserLog userLog = new UserLog(sessionUser.getUid(), UserLogType.INITIATED_USSD, "First USSD active session", UserInterfaceType.UNKNOWN);
        bundle.addLog(userLog);

        if (welcomeMessageEnabled) {

            String[] welcomeMessageIds = new String[]{
                    "sms.welcome.1",
                    "sms.welcome.2",
                    "sms.welcome.3"
            };

            for (String welcomeMessageId : welcomeMessageIds) {
                String message = messageAssemblingService.createWelcomeMessage(welcomeMessageId, sessionUser);
                WelcomeNotification notification = new WelcomeNotification(sessionUser, message, userLog);
                // notification sending delay of 2days
                ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now().plus(48, ChronoUnit.HOURS), TimeZone.getTimeZone("Africa/Johannesburg").toZoneId());
                notification.setNextAttemptTime(DateTimeUtil.restrictToDaytime(zonedDateTime.toInstant(), null, DateTimeUtil.getSAST()));
                bundle.addNotification(notification);
            }
        }

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String[]> findOthersInGraph(User user, String nameFragment) {
        // todo : fix this causing errors when groups is empty (and also profile that collection call on getGroups)
        // note : there is probably a way to avoid the getGroups and use criteria builder on a join
        List<User> records = userRepository.findAll(Specifications.where(
                nameContains(nameFragment)).and(
                inGroups(user.getGroups())));

        return records.stream()
                .map(u -> new String[] { u.getDisplayName(), u.getPhoneNumber() })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateDisplayName(String userUid, String displayName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(displayName);

        User user = userRepository.findOneByUid(userUid);
        user.setDisplayName(displayName);
        user.setHasSetOwnName(true);
    }

    @Override
    @Transactional
    public void setDisplayNameByOther(String updatingUserUid, String targetUserUid, String displayName) {
        Objects.requireNonNull(updatingUserUid);
        Objects.requireNonNull(targetUserUid);
        Objects.requireNonNull(displayName);

        User updatingUser = userRepository.findOneByUid(updatingUserUid); // major todo : check if user is in graph
        User targetUser = userRepository.findOneByUid(targetUserUid);

        if (targetUser.isHasSetOwnName()) {
            throw new AccessDeniedException("Error! User has set their own name, only they can update it");
        }

        targetUser.setDisplayName(displayName);
    }

    @Override
    @Transactional
    public void updateUserLanguage(String userUid, Locale locale) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(locale);

        User user = userRepository.findOneByUid(userUid);
        user.setLanguageCode(locale.getLanguage());

        log.info("set the user language to : {} ", user.getLanguageCode());

        cacheUtilService.putUserLanguage(user.getPhoneNumber(), locale.getLanguage());
    }

    @Override
    @Transactional
    public void updateAlertPreferences(String userUid, AlertPreference alertPreference) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(alertPreference);

        User user = userRepository.findOneByUid(userUid);
        user.setAlertPreference(alertPreference);
        user.setNotificationPriority(alertPreference.getPriority());
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
