package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.User;

/**
 * Created by paballo on 2016/07/12.
 */
public interface AddressRepository extends JpaRepository<Address, Long>, JpaSpecificationExecutor<Address> {

    // should enforce uniqueness on resident and primary but for now taking top / latest
    Address findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(User user);

    Address findOneByUid(String uid);

}