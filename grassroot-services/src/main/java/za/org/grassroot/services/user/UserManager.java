package za.org.grassroot.services.user;

import org.apache.commons.text.RandomStringGenerator;
import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.UserRequestRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.UserSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
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
import java.util.*;
import java.util.stream.Collectors;

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
    private RoleRepository roleRepository;
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

    private RandomStringGenerator randomStringGenerator = new RandomStringGenerator.Builder().withinRange('a', 'z').build();

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
        if (StringUtils.isEmpty(userProfile.getPhoneNumber()) && StringUtils.isEmpty(userProfile.getEmailAddress())) {
            throw new IllegalArgumentException("User phone number or email address is required");
        }

        User userToSave;
        String phoneNumber = StringUtils.isEmpty(userProfile.getPhoneNumber()) ? null : PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());
        String emailAddress = userProfile.getEmailAddress();
        long start = System.nanoTime();
        boolean userExists = userRepository.existsByPhoneNumber(phoneNumber)
                || (emailAddress != null && userRepository.existsByEmail(emailAddress));
        long time = System.nanoTime() - start;
        log.info("User exists check took {} nanosecs", time);

        if (userExists) {

            log.info("The user exists, and their web profile is set to: " + userProfile.isHasWebProfile());

            User userToUpdate = findByNumberOrEmail(phoneNumber, emailAddress);
            if (!StringUtils.isEmpty(userToUpdate.getPassword())) {
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a password protected profile!");
            }

            // if we reach here, means user 'exists' via group addition etc., but hasn't registered, so take the details
            // as theirs, and set a password etc ...
            if (!userToUpdate.hasName()) {
                userToUpdate.setDisplayName(userProfile.getDisplayName());
                userToUpdate.setHasSetOwnName(true);
            }

            if (StringUtils.isEmpty(userToUpdate.getEmailAddress()) && !StringUtils.isEmpty(emailAddress)) {
                userToUpdate.setEmailAddress(emailAddress);
            }

            if (StringUtils.isEmpty(userToUpdate.getPhoneNumber()) && !StringUtils.isEmpty(phoneNumber)) {
                userToUpdate.setPhoneNumber(phoneNumber);
            }

            userToUpdate.setHasWebProfile(true);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {
            userToSave = new User(phoneNumber, userProfile.getDisplayName(), userProfile.getEmailAddress());
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
        User userProfile = new User(userDTO.getPhoneNumber(), userDTO.getDisplayName(), null);
        User userToSave;
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());
        boolean userExists = userExist(phoneNumber);

        if (userExists) {

            User userToUpdate = userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
            if (userToUpdate.hasAndroidProfile() && userToUpdate.getMessagingPreference().equals(DeliveryRoute.ANDROID_APP)) {
                log.warn("User already has android profile");
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a android profile!");
            }

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setHasAndroidProfile(true);
            userToUpdate.setMessagingPreference(DeliveryRoute.ANDROID_APP);
            userToUpdate.setAlertPreference(AlertPreference.NOTIFY_NEW_AND_REMINDERS);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {

            String newPassword = randomStringGenerator.generate(6);
            String encodedPassword = passwordEncoder.encode(newPassword);

            userProfile.setPhoneNumber(phoneNumber);
            userProfile.setUsername(phoneNumber);
            userProfile.setPassword(encodedPassword);
            userProfile.setDisplayName(userDTO.getDisplayName());
            userProfile.setHasSetOwnName(true);
            userProfile.setHasAndroidProfile(true);
            userProfile.setMessagingPreference(DeliveryRoute.ANDROID_APP);
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

    // a lot of potential for things to go wrong in here, hence a lot of checks - in general, this one is hairy
    @Override
    @Transactional
    public boolean updateUser(String userUid, String displayName, String phoneNumber, String emailAddress,
                              Province province, AlertPreference alertPreference, Locale locale, String validationOtp) {
        Objects.requireNonNull(userUid);

        if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(emailAddress)) {
            throw new IllegalArgumentException("Error! Cannot set both phone number and email address to null");
        }

        User user = userRepository.findOneByUid(userUid);
        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);

        boolean phoneChanged = !StringUtils.isEmpty(user.getPhoneNumber()) && !user.getPhoneNumber().equals(msisdn);
        boolean emailChanged = !StringUtils.isEmpty(user.getEmailAddress()) && !user.getEmailAddress().equals(emailAddress);
        boolean otherChanged = false;

        if ((phoneChanged || emailChanged) && StringUtils.isEmpty(validationOtp)) {
            return false;
        }

        if ((phoneChanged || emailChanged)) {
            passwordTokenService.validateOtp(user.getUsername(), validationOtp);
        }

        // if user set their phone number to be blank, they must have an email address, and if so, switch username to it
        if (phoneChanged && StringUtils.isEmpty(msisdn) && !user.isUsernameEmailAddress()) {
            // because of earlier check up top, if we reach here then new email address cannot be blank
            user.setUsername(emailAddress);
        }

        // as above, with email
        if (emailChanged && StringUtils.isEmpty(emailAddress) && user.isUsernameEmailAddress()) {
            user.setUsername(msisdn);
        }

        // retain this check since the (v1) client-side validation is a bit strange & unpredictable on this field
        if (!StringUtils.isEmpty(displayName)) {
            otherChanged = otherChanged || !user.getDisplayName().equals(displayName);
            user.setDisplayName(displayName);
            user.setHasSetOwnName(true);
        }

        user.setPhoneNumber(msisdn);
        if (phoneChanged && !user.isUsernameEmailAddress()) {
            user.setUsername(msisdn);
        }

        user.setEmailAddress(emailAddress);
        if (emailChanged && user.isUsernameEmailAddress()) {
            user.setUsername(emailAddress);
        }

        if (province != null) {
            otherChanged = otherChanged || !province.equals(user.getProvince());
            user.setProvince(province);
        }

        if (locale != null) {
            otherChanged = otherChanged || !locale.getLanguage().equals(user.getLanguageCode());
            user.setLanguageCode(locale.getLanguage());
        }

        if (alertPreference != null) {
            otherChanged = otherChanged || !alertPreference.equals(user.getAlertPreference());
            user.setAlertPreference(alertPreference);
            user.setNotificationPriority(alertPreference.getPriority());
        }

        log.info("okay, did anything change? {}", otherChanged);
        Set<UserLog> logs = new HashSet<>();
        if (emailChanged) {
            logs.add(new UserLog(userUid, UserLogType.USER_EMAIL_CHANGED, emailAddress, UserInterfaceType.UNKNOWN));
        }
        if (phoneChanged) {
            logs.add(new UserLog(userUid, UserLogType.USER_PHONE_CHANGED, phoneNumber, UserInterfaceType.UNKNOWN));
        }
        if (otherChanged) {
            logs.add(new UserLog(userUid, UserLogType.USER_DETAILS_CHANGED, null, UserInterfaceType.UNKNOWN));
        }

        log.info("okay, done updating, storing {} logs", logs.size());
        asyncUserService.storeUserLogs(logs);
        return true;
    }

    @Override
    public String generateAndroidUserVerifier(String phoneNumber, String displayName, String password) {
        Objects.requireNonNull(phoneNumber);
        phoneNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        if (displayName != null) {
            UserCreateRequest userCreateRequest = userCreateRequestRepository.findByPhoneNumber(phoneNumber);
            if (userCreateRequest == null) {
                userCreateRequest = new UserCreateRequest(phoneNumber, displayName, password, Instant.now());
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
        User user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
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
    public void setMessagingPreference(String userUid, DeliveryRoute preference) {
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
            User sessionUser = new User(phoneNumber, null, null);
            sessionUser.setUsername(phoneNumber);
            User newUser = userRepository.save(sessionUser);
            asyncUserService.recordUserLog(newUser.getUid(), UserLogType.CREATED_IN_DB, "Created via loadOrCreateUser");
            return newUser;
        } else {
            return userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
        }
    }

    /*
    Method to load or save a user and store that they have initiated the session. Putting it in services and making it
    distinct from standard loadOrCreateUser because we may want to optimize it aggressively in future.
     */

    @Override
    @Transactional(readOnly = true)
    public User findByInputNumber(String inputNumber) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumberAndPhoneNumberNotNull(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        if (sessionUser == null) throw new NoSuchUserException("Could not find user with phone number: " + inputNumber);
        return sessionUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findByNumberOrEmail(String inputNumber, String emailAddress) {
        String msisdn;
        try {
            msisdn = PhoneNumberUtil.convertPhoneNumber(inputNumber);
        } catch (InvalidPhoneNumberException e) {
            msisdn = null;
        }
        User user = msisdn == null || !userExist(msisdn) ?
                userRepository.findByEmailAddressAndEmailAddressNotNull(emailAddress) :
                userRepository.findByPhoneNumberAndPhoneNumberNotNull(msisdn);
        if (user == null) throw new NoSuchUserException("No user with number " + inputNumber + " or email address " + emailAddress);
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumberAndPhoneNumberNotNull(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        cacheUtilService.putUssdMenuForUser(inputNumber, currentUssdMenu);
        return sessionUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findByUsernameLoose(String userName) {
        User user = fetchUserByUsernameStrict(userName);
        if (user == null) {
            if (PhoneNumberUtil.testInputNumber(userName)) {
                user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(PhoneNumberUtil.convertPhoneNumber(userName));
            } else if (EmailValidator.getInstance().isValid(userName)) {
                user = userRepository.findByEmailAddressAndEmailAddressNotNull(userName);
            }
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> searchByGroupAndNameNumber(String groupUid, String nameOrNumber) {
        return userRepository.findByGroupsPartOfAndDisplayNameContainingIgnoreCaseOrPhoneNumberLike(
                groupRepository.findOneByUid(groupUid), "%" + nameOrNumber + "%", "%" + nameOrNumber + "%");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean doesUserHaveStandardRole(String userName, String roleName) {
        User user = findByNumberOrEmail(userName, userName);
        try {
            Role role = roleRepository.findByNameAndRoleType(roleName, Role.RoleType.STANDARD).get(0);
            return user.getStandardRoles().contains(role);
        } catch (NullPointerException e) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileStatus fetchUserProfileStatus(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return !StringUtils.isEmpty(user.getPassword()) ? UserProfileStatus.HAS_PASSWORD :
                user.isHasInitiatedSession() ? UserProfileStatus.HAS_USED_USSD : UserProfileStatus.ONLY_EXISTS;
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

        User user = new User(msisdn, null, null);
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
    public void resetUserPassword(String username, String newPassword, String token) throws InvalidTokenException {
        User user = userRepository.findByUsername(username);
        log.info("Found this user: " + user);

        if (passwordTokenService.isShortLivedOtpValid(user.getUsername(), token.trim())) {
            log.info("came in as true, with this token :" + token);
            String encodedPassword = passwordEncoder.encode(newPassword);
            user.setPassword(encodedPassword);
            user = userRepository.save(user);
            passwordTokenService.expireVerificationCode(user.getUid(), VerificationCodeType.SHORT_OTP);
        } else {
            throw new InvalidTokenException("Invalid OTP submitted");
        }
    }

    @Override
    @Transactional
    public void updateEmailAddress(String callingUserUid, String userUid, String emailAddress) {
        Objects.requireNonNull(callingUserUid);
        Objects.requireNonNull(userUid);

        validateUserCanAlter(callingUserUid, userUid);

        User user = userRepository.findOneByUid(userUid);
        if (StringUtils.isEmpty(emailAddress) && StringUtils.isEmpty(user.getPhoneNumber())) {
            throw new IllegalArgumentException("Cannot set email to empty if no phone number");
        }

        // todo : store some logs
        user.setEmailAddress(emailAddress);
    }

    @Override
    @Transactional
    public void updatePhoneNumber(String callingUserUid, String userUid, String phoneNumber) {
        Objects.requireNonNull(callingUserUid);
        Objects.requireNonNull(userUid);

        validateUserCanAlter(callingUserUid, userUid);
        User user = userRepository.findOneByUid(userUid);

        if (!StringUtils.isEmpty(phoneNumber)) {
            String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            user.setPhoneNumber(msisdn);
        } else if (!user.hasEmailAddress()) {
            throw new IllegalArgumentException("Cannot set phone number to empty if no email address");
        }
    }

    @Override
    public User fetchUserByUsernameStrict(String username) {
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
                notification.setSendOnlyAfter(DateTimeUtil.restrictToDaytime(zonedDateTime.toInstant(), null, DateTimeUtil.getSAST()));
                bundle.addNotification(notification);
            }
        }

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String[]> findOthersInGraph(User user, String nameFragment) {
        List<Group> groups = groupRepository.findAll(GroupSpecifications.userIsMemberAndCanSeeMembers(user));
        List<User> records = userRepository.findAll(UserSpecifications.withNameInGroups(nameFragment, groups));

        return records.stream()
                .map(u -> new String[] { u.getDisplayName(), u.getPhoneNumber() })
                .collect(Collectors.toList());
    }

    @Override
    public List<User> findRelatedUsers(User user, String nameFragment) {
        List<Group> groups = groupRepository.findAll(GroupSpecifications.userIsMemberAndCanSeeMembers(user));
        return userRepository.findAll(UserSpecifications.withNameInGroups(nameFragment, groups));
    }

    @Override
    @Transactional
    public void updateDisplayName(String callingUserUid, String userToUpdateUid, String displayName) {
        Objects.requireNonNull(callingUserUid);
        Objects.requireNonNull(userToUpdateUid);
        Objects.requireNonNull(displayName);

        validateUserCanAlter(callingUserUid, userToUpdateUid);
        User user = userRepository.findOneByUid(userToUpdateUid);

        user.setDisplayName(displayName);
        if (callingUserUid.equals(userToUpdateUid)) {
            user.setHasSetOwnName(true);
        }
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

    private void validateUserCanAlter(String callingUserUid, String userToUpdateUid) {
        if (!callingUserUid.equals(userToUpdateUid)) {
            User callingUser = userRepository.findOneByUid(callingUserUid);
            Role adminRole = roleRepository.findByName(BaseRoles.ROLE_SYSTEM_ADMIN).get(0);
            if (!callingUser.getStandardRoles().contains(adminRole)) {
                throw new AccessDeniedException("Error! Only user or admin can perform this update");
            }
        }
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
