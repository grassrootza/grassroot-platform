package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.UserCreateRequest;

/**
 * Created by paballo on 2016/03/14.
 */
public interface UserRequestRepository extends JpaRepository<UserCreateRequest, Long>{

    UserCreateRequest findByPhoneNumber(String phoneNumber);


}
