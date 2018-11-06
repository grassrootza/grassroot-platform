package za.org.grassroot.services.user;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
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
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.EventNotification;
import za.org.grassroot.core.domain.notification.WelcomeNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.repository.UserRequestRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.NotificationSpecifications;
import za.org.grassroot.core.specifications.UserSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.graph.GraphBroker;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.exception.InvalidOtpException;
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
@Slf4j
@Service
@Transactional
public class UserManager implements UserManagementService, UserDetailsService {

    @Value("${grassroot.languages.notify.mincount:3}")
    protected int MIN_NOTIFICATIONS_FOR_LANG_PING;

    private static final String EXPIRED_USER_NAME = "del_user_";

    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordTokenService passwordTokenService;
    @Autowired private CacheUtilService cacheUtilService;
    @Autowired private AsyncUserLogger asyncUserService;
    @Autowired private UserRequestRepository userCreateRequestRepository;
    @Autowired private LogsAndNotificationsBroker logsAndNotificationsBroker;
    @Autowired private MessageAssemblingService messageAssemblingService;
    @Autowired private MessagingServiceBroker messagingServiceBroker;
    @Autowired(required = false) private GraphBroker graphBroker;

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
    @Transactional(readOnly = true)
    public List<User> load(Set<String> userUids) {
        return userRepository.findByUidIn(userUids);
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
        log.info("about to try use & convert phone & email : {}", userProfile.getPhoneNumber(), userProfile.getEmailAddress());
        String phoneNumber = StringUtils.isEmpty(userProfile.getPhoneNumber()) ? null : PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());
        String emailAddress = userProfile.getEmailAddress();
        long start = System.nanoTime();
        boolean userExists = (!StringUtils.isEmpty(phoneNumber) && userRepository.existsByPhoneNumber(phoneNumber))
                || (!StringUtils.isEmpty(emailAddress) && userRepository.existsByEmail(emailAddress));
        long time = System.nanoTime() - start;
        log.info("User exists check took {} nanosecs", time);

        if (userExists) {

            log.info("The user exists, and their web profile is set to: " + userProfile.isHasWebProfile());

            User userToUpdate = findByNumberOrEmail(phoneNumber, emailAddress);
            if (userToUpdate.hasPassword()) {
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
            userToSave = userToUpdate;
        } else {
            userToSave = new User(phoneNumber, userProfile.getDisplayName(), userProfile.getEmailAddress());
            userToSave.setHasWebProfile(true);
            userToSave.setHasSetOwnName(true);
        }

        userToSave.setHasInitiatedSession(true);
        Role fullUserRole = roleRepository.findByNameAndRoleType(BaseRoles.ROLE_FULL_USER, Role.RoleType.STANDARD).get(0);
        userToSave.addStandardRole(fullUserRole);

        if (passwordEncoder != null) {
            userToSave.setPassword(passwordEncoder.encode(userProfile.getPassword()));
        } else {
            log.warn("PasswordEncoder not set, skipping password encryption...");
        }

        try {
            User userToReturn = userRepository.saveAndFlush(userToSave);
            if (!userExists)
                asyncRecordNewUser(userToReturn.getUid(), "Web", UserInterfaceType.WEB_2);
            asyncUserService.recordUserLog(userToReturn.getUid(), UserLogType.CREATED_WEB, "User created web profile", null);
            return userToReturn;
        } catch (final Exception e) {
            throw new UserExistsException("User '" + userProfile.getUsername() + "' already exists!");
        }
    }

    private void asyncRecordNewUser(final String userUid, final String logDescription, UserInterfaceType channel) {
        asyncUserService.recordUserLog(userUid, UserLogType.CREATED_IN_DB, logDescription, channel);
        if (graphBroker != null) {
            graphBroker.addUserToGraph(userUid);
            graphBroker.annotateUser(userUid, null, null, true);
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
        Role fullUserRole = roleRepository.findByNameAndRoleType(BaseRoles.ROLE_FULL_USER, Role.RoleType.STANDARD).get(0);

        if (userExists) {

            User userToUpdate = userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
            if (userToUpdate.isHasAndroidProfile() && userToUpdate.getMessagingPreference().equals(DeliveryRoute.ANDROID_APP)) {
                log.warn("User already has android profile");
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a android profile!");
            }

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setHasAndroidProfile(true);
            userToUpdate.setMessagingPreference(DeliveryRoute.ANDROID_APP);
            userToUpdate.setAlertPreference(AlertPreference.NOTIFY_NEW_AND_REMINDERS);
            userToUpdate.setHasInitiatedSession(true);
            userToUpdate.addStandardRole(fullUserRole);
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
            userProfile.setHasInitiatedSession(true);
            userProfile.addStandardRole(fullUserRole);
            userProfile.setMessagingPreference(DeliveryRoute.ANDROID_APP);
            userProfile.setAlertPreference(AlertPreference.NOTIFY_NEW_AND_REMINDERS);

            userToSave = userProfile;
        }

        try {
            User userToReturn = userRepository.saveAndFlush(userToSave);
            if (!userExists)
                asyncRecordNewUser(userToReturn.getUid(), "Android", UserInterfaceType.ANDROID_2);
            asyncUserService.recordUserLog(userToReturn.getUid(), UserLogType.REGISTERED_ANDROID, "User created android profile",
                    UserInterfaceType.ANDROID);
            return userToReturn;
        } catch (final Exception e) {
            e.printStackTrace();
            log.warn(e.getMessage());
            throw new UserExistsException("User '" + userProfile.getUsername() + "' already exists!");
        }

    }

    // a lot of potential for things to go wrong in here, hence a lot of checks - in general, this one is hairy
    @Override
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public boolean updateUser(String userUid, String displayName, String phoneNumber, String emailAddress,
                              Province province, AlertPreference alertPreference, Locale locale, String validationOtp,
                              boolean whatsappOptIn, UserInterfaceType channel) {
        Objects.requireNonNull(userUid);// added whatsapp opt in field to the updateUser method

        log.info("What is the value {}",whatsappOptIn);//Logging the event value of whats app subscription coming from profile form.
        log.info("The interface type is {} ", channel);//Loging the interface typ ewhen making updates

        if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(emailAddress)) {
            throw new IllegalArgumentException("Error! Cannot set both phone number and email address to null");
        }

        User user = userRepository.findOneByUid(userUid);
        final String msisdn = PhoneNumberUtil.convertPhoneNumber(phoneNumber);

        boolean phoneChanged = !StringUtils.isEmpty(user.getPhoneNumber()) && !user.getPhoneNumber().equals(msisdn);
        boolean emailChanged = !StringUtils.isEmpty(user.getEmailAddress()) && !user.getEmailAddress().equals(emailAddress);
        boolean whatsAppChanged = whatsappOptIn != user.isWhatsAppOptedIn();
        boolean otherChanged = false;
//        boolean subscribeWhatsapp = whatsappOptIn;// whatsapp subscribtion log boolean

        user.setWhatsAppOptedIn(whatsappOptIn);

        if ((phoneChanged || emailChanged) && StringUtils.isEmpty(validationOtp)) {
            return false;
        }

        if ((phoneChanged || emailChanged) && !passwordTokenService.isShortLivedOtpValid(user.getUsername(), validationOtp)) {
            throw new InvalidOtpException();
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

        //Storing a log for whats app subscription after changes have been altered.
        if (whatsAppChanged){
            user.setWhatsAppOptedIn(whatsappOptIn);
            logs.add(new UserLog(userUid,UserLogType.USER_SUBSCRIBED_WHATSAPP,"Subscribed for whats app notifications",
                    UserInterfaceType.WEB_2));
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
        //User user = userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
        User user = findByNumberOrEmail(phoneNumber,phoneNumber);
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
    public User loadOrCreateUser(String inputNumber, UserInterfaceType channel) {
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(inputNumber);
        log.info("Using phone number, formatted: {}", phoneNumber);
        if (!userExist(phoneNumber)) {
            User sessionUser = new User(phoneNumber, null, null);
            sessionUser.setUsername(phoneNumber);
            User newUser = userRepository.save(sessionUser);
            asyncRecordNewUser(newUser.getUid(), "Created via loadOrCreateUser", channel);
            return newUser;
        } else {
            return userRepository.findByPhoneNumberAndPhoneNumberNotNull(phoneNumber);
        }
    }

    @Override
    @Transactional
    public User loadOrCreate(String phoneOrEmail) {
        User user = findByUsernameLoose(phoneOrEmail);
        if (user != null)
            return user;

        try {
            log.info("Neither phone nor email matched existing, creating with: {}", phoneOrEmail);
            if (EmailValidator.getInstance().isValid(phoneOrEmail))
                user = new User(null, null, phoneOrEmail);
            else if (PhoneNumberUtil.testInputNumber(phoneOrEmail))
                user = new User(PhoneNumberUtil.convertPhoneNumber(phoneOrEmail), null, null);

            if (user == null)
                throw new IllegalArgumentException("Error! Phone or email is valid for neither format");

            user = userRepository.save(user);
            asyncRecordNewUser(user.getUid(), "Created via loadOrCreate", null);

            return user;
        } catch (IllegalArgumentException e) {
            log.error("Error! : {}", e.getMessage());
            return null;
        } catch (DataIntegrityViolationException e) {
            log.error("Error creating or loading user: ", e);
            return null;
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
        asyncRecordNewUser(user.getUid(), "Created via sponsorship request", null);
        return user.getUid();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userExist(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailTaken(String userUid, String email) {
        User user = userRepository.findByEmailAddressAndEmailAddressNotNull(email);
        return user != null && !user.getUid().equals(userUid);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendAndroidLinkSms(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        String message = messageAssemblingService.createAndroidLinkSms(user);
        messagingServiceBroker.sendSMS(message, user.getUid(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean needsToSetName(User user, boolean evenOnCreation) {
//        log.info("User has name? : {}, has skipped: {}", user.hasName(), asyncUserService.hasSkippedName(user.getUid()));
        return !user.hasName()
                && !asyncUserService.hasSkippedName(user.getUid())
                && (evenOnCreation || user.getCreatedDateTime().isBefore(Instant.now().minus(3, ChronoUnit.MINUTES)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean needsToSetProvince(User user, boolean evenOnCreation) {
        return user.getProvince() == null
                && !asyncUserService.hasSkippedProvince(user.getUid())
                && (evenOnCreation || user.getCreatedDateTime().isBefore(Instant.now().minus(3, ChronoUnit.MINUTES)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean needToPromptForLanguage(User user, int minSessions) {
        return !(isUserNonEnglish(user) || asyncUserService.hasChangedLanguage(user.getUid())) &&
                asyncUserService.numberSessions(user.getUid(), null, null, null) > minSessions;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean shouldSendLanguageText(User user) {
        final boolean isNonPhoneOrNonZA = !user.hasPhoneNumber() || PhoneNumberUtil.isPhoneNumberSouthAfrican(user.getPhoneNumber());
        if (isUserNonEnglish(user) || asyncUserService.hasChangedLanguage(user.getUid()))
            return false;

        Specification<Notification> totalCountSpecs = Specification.where(NotificationSpecifications.toUser(user));
        Specification<Notification> languageNotifySpecs = totalCountSpecs.and(
                NotificationSpecifications.userLogTypeIs(UserLogType.NOTIFIED_LANGUAGES));
        return logsAndNotificationsBroker.countNotifications(totalCountSpecs) > MIN_NOTIFICATIONS_FOR_LANG_PING
                && logsAndNotificationsBroker.countNotifications(languageNotifySpecs) == 0;
    }

    private boolean isUserNonEnglish(User user) {
        return !StringUtils.isEmpty(user.getLanguageCode())
                && !Locale.ENGLISH.getLanguage().equals(user.getLanguageCode());
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

        user.setEmailAddress(emailAddress);
        asyncUserService.recordUserLog(userUid, UserLogType.USER_EMAIL_CHANGED, emailAddress, null);
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
    @Transactional
    public void updateHasImage(String userUid, boolean hasImage) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        user.setHasImage(hasImage);
    }

    @Override
    @Transactional
    public void updateContactError(String userUid, boolean hasContactError) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        user.setContactError(hasContactError);
    }

    @Override
    @Transactional
    public void deleteUser(String userUid, String validationOtp) {
        User user = userRepository.findOneByUid(userUid);
        passwordTokenService.validateOtp(user.getUsername(), validationOtp);

        // step 1 : remove user from their memberships, accounts, etc.
        Set<Membership> memberships = user.getMemberships();
        log.info("user now being removed from {} groups", memberships.size());
        for (Membership membership : memberships) {
            Group group = membership.getGroup();
            group.removeMembership(membership); // concurrency?
        }
        user.setSafetyGroup(null);

        log.info("user now being removed from accounts ..");
        user.setPrimaryAccount(null);
        for (Account account : user.getAccountsAdministered()) {
            account.removeAdministrator(user);
        }

        // step 2 : set user inactive and remove their name, phone number and email address, and set their username to unique value
        log.info("now setting user disabled and removing all personal data");
        user.setEnabled(false);
        user.setDisplayName(null);
        user.setFirstName(null);
        user.setLastName(null);
        user.setPhoneNumber(null);
        user.setEmailAddress(null);
        user.setLanguageCode(null);
        user.setProvince(null);

        // possibly at some point in future may create a conflict, but pretty remote (_lots_ of deletes at once)
        log.info("altering username to unique value");
        user.setUsername(EXPIRED_USER_NAME + System.nanoTime());
        user.setPassword(null);

        // step 3 : set all boolean flags to false
        log.info("and setting all flags to false");
        user.setHasInitiatedSession(false);
        user.setHasWebProfile(false);
        user.setHasImage(false);
        user.setHasAndroidProfile(false);
        user.setHasSetOwnName(false);
        user.setContactError(false);
        user.setLiveWireContact(false);
        user.setWhatsAppOptedIn(false);

        // step 4: remove all standard roles
        user.removeAllStdRoles();

        log.info("and storing the deletion of all details");
        userRepository.saveAndFlush(user);

        log.info("user removed, now cleaning up logs ...");
        asyncUserService.removeAllUserInfoLogs(userUid);

        log.info("all clean, exiting");
    }

    @Override
    @Transactional
    public User fetchUserByUsernameStrict(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    @Transactional
    public void setHasInitiatedUssdSession(String userUid, boolean sendWelcomeMessage) {
        User sessionUser = userRepository.findOneByUid(userUid);

        sessionUser.setHasInitiatedSession(true);

        Role fullUserRole = roleRepository.findByNameAndRoleType(BaseRoles.ROLE_FULL_USER, Role.RoleType.STANDARD).get(0);
        sessionUser.addStandardRole(fullUserRole);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        UserLog userLog = new UserLog(sessionUser.getUid(), UserLogType.INITIATED_USSD, "First USSD active session", UserInterfaceType.UNKNOWN);
        bundle.addLog(userLog);

        if (welcomeMessageEnabled && sendWelcomeMessage) {

            String[] welcomeMessageIds = new String[]{
                    "sms.welcome.1",
                    "sms.welcome.2"
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
    public void updateUserLanguage(String userUid, Locale locale, UserInterfaceType channel) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(locale);

        User user = userRepository.findOneByUid(userUid);
        user.setLanguageCode(locale.getLanguage());

        log.info("set the user language to : {} ", user.getLanguageCode());

        asyncUserService.recordUserLog(userUid, UserLogType.CHANGED_LANGUAGE, locale.getLanguage(), channel);
        cacheUtilService.putUserLanguage(user.getPhoneNumber(), locale.getLanguage());
    }

    @Override
    @Transactional
    public void updateUserProvince(String userUid, Province province) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        user.setProvince(province);
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

    @Override
    @Transactional
    public UserRegPossibility checkUserCanRegister(String phone, String email){
        try{
            User user = findByNumberOrEmail(phone, email);
            if (user == null)
                return UserRegPossibility.USER_CAN_REGISTER;

            if (user.hasPassword())
                return UserRegPossibility.USER_CANNOT_REGISTER;

            // last case: user exists but has no password set
            passwordTokenService.triggerOtp(user);
            return UserRegPossibility.USER_REQUIRES_OTP;
        }catch (NoSuchUserException e){
            return UserRegPossibility.USER_CAN_REGISTER;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findUsersThatRsvpForEvent(Event event, EventRSVPResponse response) {
        return EventRSVPResponse.YES.equals(response) ? userRepository.findUsersThatRSVPYesForEvent(event) :
                EventRSVPResponse.NO.equals(response) ? userRepository.findUsersThatRSVPNoForEvent(event) :
                        userRepository.findUsersThatRSVPForEvent(event);
    }

    @Override
    public List<User> findUsersNotifiedAboutEvent(Event event, Class<? extends EventNotification> notificationClass) {
        return userRepository.findNotificationTargetsForEvent(event, notificationClass);
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
