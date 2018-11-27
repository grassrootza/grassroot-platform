package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.Address;

import java.util.List;

/**
 * Created by paballo on 2016/07/12.
 */
public interface AddressRepository extends JpaRepository<Address, Long>, JpaSpecificationExecutor<Address> {

    // should enforce uniqueness on resident and primary but for now taking top / latest
    Address findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(User user);

    Address findOneByUid(String uid);

    @Query("select * from address ad where ad.latitude is not null " +
            "and ad.resident_user_id in (" +
            "select id from user_profile where uid not in (select user_uid from user_location_log))")
    List<Address> loadAddressesWithLocation();

}