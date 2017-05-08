package za.org.grassroot.services.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.AddressRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.async.AsyncUserLogger;

import java.util.Objects;

/**
 * Created by paballo on 2016/07/14.
 */
@Service
public class AddressBrokerImpl implements AddressBroker {


    private Logger log = LoggerFactory.getLogger(AddressBrokerImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private AsyncUserLogger asyncUserLogger;


    @Override
    @Transactional(readOnly = true)
    public Address getUserAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return addressRepository.findOneByResident(user);
    }

    @Override
    @Transactional
    public void updateUserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findOneByResident(user);

        if (address == null) {
            address = createAddress(user);
        }

        if (!StringUtils.isEmpty(houseNumber))
            address.setHouse(houseNumber);
        if (!StringUtils.isEmpty(street))
            address.setStreet(street);
        if (!StringUtils.isEmpty(town))
            address.setNeighbourhood(town);;

        log.info("updated user address");
    }

    private Address createAddress(User resident) {
        Address address = new Address(resident);
        addressRepository.save(address);
        log.info("Added user address to db");
        asyncUserLogger.recordUserLog(resident.getUid(), UserLogType.ADDED_ADDRESS, "user added address");
        return address;
    }


    @Override
    @Transactional
    public void removeAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findOneByResident(user);
        addressRepository.delete(address);
        asyncUserLogger.recordUserLog(user.getUid(), UserLogType.REMOVED_ADDRESS, "user deleted address");
        log.info("deleting user address from db");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAddress(String userUid) {
        final User user = userRepository.findOneByUid(userUid);
        final Address address = addressRepository.findOneByResident(user);
        return address != null && address.hasHouseAndStreet();
    }

}
