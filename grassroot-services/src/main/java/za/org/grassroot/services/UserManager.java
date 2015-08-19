package za.org.grassroot.services;

import com.google.common.collect.Lists;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * @author Lesetse Kimwaga
 */

@Service
@Transactional
public class UserManager implements UserManagementService, UserDetailsService {

    private static final int PAGE_SIZE = 50;

    @Autowired
    private UserRepository userRepository;

    @Override
    public User createUserProfile(User userProfile) {
        return userRepository.save(userProfile);
    }

    @Override
    public List<User> getAllUsers() {
        return Lists.newArrayList(userRepository.findAll());
    }

    @Override
    public Page<User> getDeploymentLog(Integer pageNumber) {

        PageRequest request = new PageRequest(pageNumber - 1, PAGE_SIZE, Sort.Direction.DESC);
        return userRepository.findAll(request);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        List<User> users = userRepository.findByUsername(username);

        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Username not found.");
        }
        return users.get(0);
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
        if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
            User sessionUser = new User();
            sessionUser.setPhoneNumber(phoneNumber);
            return userRepository.save(sessionUser);
        } else {
            return userRepository.findByPhoneNumber(phoneNumber).iterator().next();
        }
    }

    @Override
    public User findByInputNumber(String inputNumber) throws NoSuchElementException {
        return userRepository.findByPhoneNumber(convertPhoneNumber(inputNumber)).iterator().next();
    }

    @Override
    public List<User> getUsersFromNumbers(String listOfNumbers) {
        List<User> usersToReturn = new ArrayList<User>();

        // todo: make less strong assumptions that users are perfectly well behaved ...

        listOfNumbers = listOfNumbers.replace("\"", ""); // in case the response is passed with quotes around it
        List<String> splitNumbers = Arrays.asList(listOfNumbers.split(" "));
        List<User> usersToAdd = new ArrayList<User>();

        for (String inputNumber : splitNumbers) {
            String phoneNumber = UserManager.convertPhoneNumber(inputNumber);
            if (userRepository.findByPhoneNumber(phoneNumber).isEmpty()) {
                User userToCreate = new User();
                userToCreate.setPhoneNumber(phoneNumber);
                userRepository.save(userToCreate); // removing in deployment, so don't swamp Heroku DB with crud
                usersToAdd.add(userToCreate);
            } else {
                usersToAdd.add(userRepository.findByPhoneNumber(phoneNumber).iterator().next());
            }
        }
        return usersToAdd;
    }

    /**
     * Moving some functions from the controller classes here, to handle phone number strings given by users
     * todo: Move the country code definition into a properties file ?
     */

    public static String convertPhoneNumber(String inputString) {

        try {
            PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(inputString, "ZA");

            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumberUtil.format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164).replace("+", "");
            } else {
                throw new RuntimeException("Could not format phone number '" + inputString + "'");
            }

        } catch (NumberParseException e) {
            throw new RuntimeException("Could not format phone number '" + inputString + "'");
        }

    }



    public static String invertPhoneNumber(String storedNumber) {

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
}
