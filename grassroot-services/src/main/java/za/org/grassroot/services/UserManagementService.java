package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.User;

import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface UserManagementService {

    User createUserProfile(User userProfile);

    List<User> getAllUsers();

    public Page<User> getDeploymentLog(Integer pageNumber);
}
