package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/07/12.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {

    Address findOneByResident(User user);

    Address findOneByUid(String uid);



}
