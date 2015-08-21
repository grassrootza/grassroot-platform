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

    public Page<User> getDeploymentLog(Integer pageNumber);

    User save(User userToSave);

    User loadOrSaveUser(String inputNumber);

    User findByInputNumber(String inputNumber);

    List<User> getUsersFromNumbers(String listOfNumbers);

    public boolean userExist(String phoneNumber);



}
