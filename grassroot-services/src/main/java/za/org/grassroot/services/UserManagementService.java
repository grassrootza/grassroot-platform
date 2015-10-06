package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface UserManagementService {

    User createUserProfile(User userProfile);

    User createUserWebProfile(User userProfile) throws UserExistsException;

    List<User> getAllUsers();

    User getUserById(Long userId);

    Page<User> getDeploymentLog(Integer pageNumber);

    User save(User userToSave);

    User loadOrSaveUser(String inputNumber);

    User loadOrSaveUser(String inputNumber, String currentUssdMenu);

    User findByInputNumber(String inputNumber) throws NoSuchUserException;

    User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException;

    User reformatPhoneNumber(User sessionUser);

    List<User> getUsersFromNumbers(List<String> listOfNumbers);

    boolean userExist(String phoneNumber);

    boolean needsToRenameSelf(User sessionUser);

    boolean needsToRSVP(User sessionUser);
    
    User resetUserPassword(String username, String newPassword, String token);

    User fetchUserByUsername(String username);

    String getLastUssdMenu(User sessionUser);


}
