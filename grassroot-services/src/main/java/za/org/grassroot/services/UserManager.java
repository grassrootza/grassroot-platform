package za.org.grassroot.services;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import javax.transaction.Transactional;
import java.util.*;

/**
 * @author Lesetse Kimwaga
 */

@Service
@Transactional
public class UserManager implements UserManagementService, UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserManager.class);

    private static final int PAGE_SIZE = 50;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    private PasswordTokenService passwordTokenService;

    @Autowired
    private EventManagementService eventManagementService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;


    @Override
    public User createUserProfile(User userProfile) {
        return userRepository.save(userProfile);
    }

    @Override
    public User createUserWebProfile(User userProfile) throws UserExistsException {

        Assert.notNull(userProfile, "User is required");
        Assert.hasText(userProfile.getPhoneNumber(), "User phone number is required");

        User userToSave;
        String phoneNumber = PhoneNumberUtil.convertPhoneNumber(userProfile.getPhoneNumber());

        if (userExist(phoneNumber)) {

            System.out.println("The user exists, and their web profile is set to: " + userProfile.getWebProfile());

            User userToUpdate = loadOrSaveUser(phoneNumber);
            if (userToUpdate.getWebProfile() == true) {
                System.out.println("This user has a web profile already");
                throw new UserExistsException("User '" + userProfile.getUsername() + "' already has a web profile!");
            }

            if (!userToUpdate.hasName()) {
                userToUpdate.setDisplayName(userProfile.getFirstName() + " " + userProfile.getLastName());
            }

            userToUpdate.setFirstName(userProfile.getFirstName());
            userToUpdate.setLastName(userProfile.getLastName());

            userToUpdate.setUsername(phoneNumber);
            userToUpdate.setWebProfile(true);
            userToUpdate.setHasInitiatedSession(true);
            userToSave = userToUpdate;

        } else {

            userProfile.setPhoneNumber(phoneNumber);
            userProfile.setUsername(phoneNumber);
            // for some reason String.join was not inserting the space properly, so changing to a straight concatenation;
            userProfile.setDisplayName(userProfile.getFirstName() + " " + userProfile.getLastName());
            userProfile.setWebProfile(true);
            userProfile.setHasInitiatedSession(true); // since signing up on web page presumes an active user
            userToSave = userProfile;
        }

        if (passwordEncoder != null) {
            userToSave.setPassword(passwordEncoder.encode(userProfile.getPassword()));
        } else {
            log.warn("PasswordEncoder not set, skipping password encryption...");
        }

        try {
            return userRepository.save(userToSave);
        } catch (final Exception e) {
            e.printStackTrace();
            log.warn(e.getMessage());
            throw new UserExistsException("User '" + userProfile.getUsername() + "' already exists!");
        }

    }

    @Override
    public List<User> getAllUsers() {
        return Lists.newArrayList(userRepository.findAll());
    }

    @Override
    public Integer getUserCount() {
        // todo: switch this to a count query in repository, though, won't be called often, so not urgent
        return getAllUsers().size();
    }

    @Override
    public User getUserById(Long userId) {
        return userRepository.findOne(userId);
    }

    @Override
    public Page<User> getDeploymentLog(Integer pageNumber) {

        PageRequest request = new PageRequest(pageNumber - 1, PAGE_SIZE, Sort.Direction.DESC);
        return userRepository.findAll(request);
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
            User sessionUser = new User();
            sessionUser.setPhoneNumber(phoneNumber);
            sessionUser.setUsername(phoneNumber);
            return userRepository.save(sessionUser);
        } else {
            return userRepository.findByPhoneNumber(phoneNumber);
        }
    }

    /**
     * Methods to keep track also of where a user is in the ussd menu flow, so can return them to that spot if time out
     */
    public User loadOrSaveUser(String inputNumber, String currentUssdMenu) {
        User sessionUser = loadOrSaveUser(inputNumber);
        log.info("USSD menu passed to services: " + currentUssdMenu);
        sessionUser.setLastUssdMenu(currentUssdMenu);
        sessionUser = userRepository.save(sessionUser);
        log.info("USSD menu stored: " + sessionUser.getLastUssdMenu());
        return sessionUser;
    }

    /*
    Method to load or save a user and store that they have initiated the session. Putting it in services and making it
    distinct from standard loadOrSaveUser because we may want to optimize it aggressively in future.
     */

    @Override
    public User loadOrSaveUser(String inputNumber, boolean isInitiatingSession) {
        User sessionUser = loadOrSaveUser(inputNumber);
        sessionUser.setHasInitiatedSession(isInitiatingSession);
        return userRepository.save(sessionUser);
    }

    /**
     * Method used in web application, which takes a half-formed user from Thymeleaf (or whatever view technology) and
     * first checks if a user with that phone number exists, and what information we do/don't have, then updates accordingy
     */
    @Override
    public User loadOrSaveUser(User passedUser) {

        // principal requirement is a non-zero phone number
        if (passedUser.getPhoneNumber() != null && !passedUser.getPhoneNumber().trim().equals("")) {
            // if we have a phone number, use it to either load a user or create one
            User loadedUser = loadOrSaveUser(passedUser.getPhoneNumber());

            // if the user doesn't have a name, but we do have one from the passed user, set the name accordingly
            if (!loadedUser.hasName() && passedUser.getDisplayName() != null) {
                loadedUser.setDisplayName(passedUser.getDisplayName());
                loadedUser = save(loadedUser);
            }

            // todo: fill out other data

            return userRepository.save(loadedUser);

        }

        return null;
    }

    @Override
    public User findByInputNumber(String inputNumber) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        if (sessionUser == null) throw new NoSuchUserException("Could not find user with phone number: " + inputNumber);
        return sessionUser;
    }

    @Override
    public User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumber(PhoneNumberUtil.convertPhoneNumber(inputNumber));
        sessionUser.setLastUssdMenu(currentUssdMenu);
        return userRepository.save(sessionUser);
    }

    @Override
    public List<User> searchByInputNumber(String inputNumber) {
        String phoneNumber = PhoneNumberUtil.convertPhoneNumberFragment(inputNumber);
        return userRepository.findByPhoneNumberContaining(phoneNumber);
    }

    @Override
    public List<User> searchByDisplayName(String displayName) {
        return userRepository.findByDisplayNameContaining(displayName);
    }

    @Override
    public List<User> searchByGroupAndNameNumber(Long groupId, String nameOrNumber) {
        return userRepository.findByGroupsPartOfAndDisplayNameContainingOrPhoneNumberContaining(
                groupManagementService.loadGroup(groupId), nameOrNumber, nameOrNumber);
    }

    @Override
    public User reformatPhoneNumber(User sessionUser) {
        String correctedPhoneNumber = PhoneNumberUtil.convertPhoneNumber(sessionUser.getPhoneNumber());
        sessionUser.setPhoneNumber(correctedPhoneNumber);
        return userRepository.save(sessionUser);
    }

    @Override
    public List<User> getUsersFromNumbers(List<String> listOfNumbers) {

        List<User> usersToAdd = new ArrayList<User>();

        for (String inputNumber : listOfNumbers) {
            String phoneNumber = PhoneNumberUtil.convertPhoneNumber(inputNumber);
            if (!userExist(phoneNumber)) {
                User userToCreate = new User();
                userToCreate.setPhoneNumber(phoneNumber);
                userRepository.save(userToCreate);
                usersToAdd.add(userToCreate);
            } else {
                usersToAdd.add(findByInputNumber(inputNumber));
            }
        }
        return usersToAdd;
    }

    @Override
    public boolean userExist(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public boolean isFirstInitiatedSession(String phoneNumber) {
        return loadOrSaveUser(phoneNumber).isHasInitiatedSession();
    }

    @Override
    public boolean isFirstInitiatedSession(User user) {
        // may want to reload from DB, but could slow it down quite a bit
        return !user.isHasInitiatedSession();
    }

    @Override
    public boolean needsToRenameSelf(User sessionUser) {
        return sessionUser.needsToRenameSelf(10); // 5 min gap as placeholder for now, to make more a session count if possible
    }

    @Override
    public boolean needsToRSVP(User sessionUser) {
        // todo: as noted elsewhere, probably want to optimize this quite aggressively
        return !(eventManagementService.getOutstandingRSVPForUser(sessionUser).size() == 0);
    }

    @Override
    public boolean needsToVote(User sessionUser) {
        log.info("Checking if vote outstanding for user: " + sessionUser);
        return !(eventManagementService.getOutstandingVotesForUser(sessionUser).size() == 0);
    }

    @Override
    public boolean needsToVoteOrRSVP(User sessionUser) {
        // todo: make this into a single combined (and optimized) query
        // note: in effect this is going to run getOutstanding twice, once here, second time to distinguish
        // todo: instead make this return EventType or null, or something like that, so only need once
        log.info("Checking if user needs to respond to something: " + sessionUser);
        return (needsToVote(sessionUser) || needsToRSVP(sessionUser));
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

    /*
    Method for an admin user to be able to reset a password for a user, if they don't have a means to get the token
    Notes: This really should be made a temporary password that requires the user to generate it when they log in
    Also, need to add the various permissions to make sure the admin user is admin, etc etc
     */

    @Override
    public User resetUserPassword(String username, String newPassword, User adminUser, String adminPassword) {

        User userToReset = userRepository.findByUsername(PhoneNumberUtil.convertPhoneNumber(username));

        try {

            // Authentication authentication = new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities());
            String encodedPassword = passwordEncoder.encode(newPassword);
            userToReset.setPassword(encodedPassword);
            userToReset = userRepository.save(userToReset);

        } catch (Exception e) {
            throw new AuthenticationServiceException("Error, admin user could not be authenticated.");
        }

        return userToReset;
    }

    @Override
    public User fetchUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public User setInitiatedSession(User sessionUser) {
        sessionUser.setHasInitiatedSession(true);
        jmsTemplateProducerService.sendWithNoReply("welcome-messages", new UserDTO(sessionUser));
        return userRepository.save(sessionUser);
    }

    @Override
    public User loadUser(Long userId) {
        return userRepository.findOne(userId);
    }

    @Override
    public String getLastUssdMenu(User sessionUser) {
        return (sessionUser.getLastUssdMenu() == null) ? "" : sessionUser.getLastUssdMenu();
    }

    @Override
    public User resetLastUssdMenu(User sessionUser) {
        sessionUser.setLastUssdMenu(null);
        return userRepository.save(sessionUser);
    }

    @Override
    public User setLastUssdMenu(User sessionUser, String lastUssdMenu) {
        sessionUser.setLastUssdMenu(lastUssdMenu);
        return userRepository.save(sessionUser);
    }

    @Override
    public User setDisplayName(User user, String displayName) {
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    @Override
    public User setUserLanguage(User sessionUser, String locale) {
        sessionUser.setLanguageCode(locale);
        return userRepository.save(sessionUser);
    }

    @Override
    public User setUserLanguage(Long userId, String locale) {
        return setUserLanguage(getUserById(userId), locale);
    }

    @Override
    public String getUserLocale(User sessionUser) {

        if (sessionUser.getLanguageCode() == null || sessionUser.getLanguageCode().trim().equals(""))
            return "en";
        else
            return sessionUser.getLanguageCode();

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

    public void setPasswordEncoder(final PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
}
