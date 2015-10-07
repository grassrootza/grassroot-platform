package za.org.grassroot.services;

import com.google.common.collect.Lists;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;

import javax.transaction.Transactional;
import java.util.*;

/**
 * @author Lesetse Kimwaga
 */

@Service
@Transactional
public class UserManager implements UserManagementService, UserDetailsService {

    private Logger log = LoggerFactory.getLogger(UserManager.class);

    private static final int PAGE_SIZE = 50;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordTokenService passwordTokenService;

    @Autowired
    private EventManagementService eventManagementService;

    @Override
    public User createUserProfile(User userProfile) {
        return userRepository.save(userProfile);
    }

    @Override
    public User createUserWebProfile(User userProfile) throws UserExistsException {

        Assert.notNull(userProfile, "User is required");
        Assert.hasText(userProfile.getPhoneNumber(), "User phone number is required");

        User userToSave;
        String phoneNumber = convertPhoneNumber(userProfile.getPhoneNumber());

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
            userToSave = userToUpdate;

        } else {

            userProfile.setPhoneNumber(phoneNumber);
            userProfile.setUsername(phoneNumber);
            // for some reason String.join was not inserting the space properly, so changing to a straight concatenation;
            userProfile.setDisplayName(userProfile.getFirstName() + " " + userProfile.getLastName());
            userProfile.setWebProfile(true);

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
        String phoneNumber = convertPhoneNumber(inputNumber);
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
        User sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber));
        if (sessionUser == null) throw new NoSuchUserException("Could not find user with phone number: " + inputNumber);
        return sessionUser;
    }

    @Override
    public User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException {
        User sessionUser = userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber));
        sessionUser.setLastUssdMenu(currentUssdMenu);
        return userRepository.save(sessionUser);
    }

    @Override
    public User reformatPhoneNumber(User sessionUser) {
        String correctedPhoneNumber = convertPhoneNumber(sessionUser.getPhoneNumber());
        sessionUser.setPhoneNumber(correctedPhoneNumber);
        return userRepository.save(sessionUser);
    }

    @Override
    public List<User> getUsersFromNumbers(List<String> listOfNumbers) {

        List<User> usersToAdd = new ArrayList<User>();

        for (String inputNumber : listOfNumbers) {
            String phoneNumber = UserManager.convertPhoneNumber(inputNumber);
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
    public boolean needsToRenameSelf(User sessionUser) {
        return sessionUser.needsToRenameSelf(5); // 5 min gap as placeholder for now, to make more a session count if possible
    }

    @Override
    public boolean needsToRSVP(User sessionUser) {
        // todo: as noted elsewhere, probably want to optimize this quite aggressively
        return !(eventManagementService.getOutstandingRSVPForUser(sessionUser).size() == 0);
    }

    @Override
    public User resetUserPassword(String username, String newPassword, String token) {

        User user = userRepository.findByUsername(username);

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
    public String getLastUssdMenu(User sessionUser) {
        return (sessionUser.getLastUssdMenu() == null) ? "" : sessionUser.getLastUssdMenu();
    }

    /**
     * Moving some functions from the controller classes here, to handle phone number strings given by users
     * todo: Move the country code definition into a properties file ?
     * todo: call these from the (Grassroot) PhoneNumberUtil class instead of here? In general need to clean up phone # handling
     */

    public static String convertPhoneNumber(String inputString) throws InvalidPhoneNumberException {

        try {
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputString.trim(), "ZA");

            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "");
            } else {
                throw new InvalidPhoneNumberException("Could not format phone number '" + inputString + "'");
            }

        } catch (NumberParseException e) {
            throw new InvalidPhoneNumberException("Could not format phone number '" + inputString + "'");
        }

    }

    public static String invertPhoneNumber(String storedNumber) throws InvalidPhoneNumberException {

        // todo: handle error if number has gotten into database in incorrect format
        // todo: make this much faster, e.g., use a simple regex / split function?

        List<String> numComponents = new ArrayList<>();
        String prefix = String.join("", Arrays.asList("0", storedNumber.substring(2, 4)));
        String midnumbers, finalnumbers;

        try {
            midnumbers = storedNumber.substring(4, 7);
            finalnumbers = storedNumber.substring(7, 11);
        } catch (Exception e) { // in case the string doesn't have enough digits ...
            midnumbers = storedNumber.substring(4);
            finalnumbers = "";
        }

        return String.join(" ", Arrays.asList(prefix, midnumbers, finalnumbers));
    }

    public void setPasswordEncoder(final PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
}
