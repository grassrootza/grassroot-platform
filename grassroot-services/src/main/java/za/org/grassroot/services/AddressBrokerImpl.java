package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public void adduserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        Address address = new Address(user, houseNumber, street, town);
        addressRepository.save(address);
        log.info("Added user address to db");

        asyncUserLogger.recordUserLog(user.getUid(), UserLogType.ADDED_ADDRESS, "user added address");
    }

    @Override
    @Transactional
    public void updateUserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findOneByResident(user);
        if (houseNumber != null) address.setHouseNumber(houseNumber);
        if (street != null) address.setStreetName(street);
        if (town != null) address.setTown(town);;

        log.info("updating user address");
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
        User user = userRepository.findOneByUid(userUid);
        return addressRepository.findOneByResident(user) != null;
    }

}
