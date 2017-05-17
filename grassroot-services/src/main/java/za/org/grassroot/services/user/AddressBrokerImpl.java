package za.org.grassroot.services.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.AddressLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.AddressLogType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.AddressLogRepository;
import za.org.grassroot.core.repository.AddressRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.async.AsyncUserLogger;

import java.util.List;
import java.util.Objects;

import static za.org.grassroot.core.specifications.AddressSpecifications.forUser;
import static za.org.grassroot.core.specifications.AddressSpecifications.matchesStreetArea;

/**
 * Created by paballo on 2016/07/14.
 */
@Service
public class AddressBrokerImpl implements AddressBroker {

    private static final Logger log = LoggerFactory.getLogger(AddressBrokerImpl.class);

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AsyncUserLogger asyncUserLogger;
    private final AddressLogRepository addressLogRepository;

    @Autowired
    public AddressBrokerImpl(UserRepository userRepository, AddressRepository addressRepository, AsyncUserLogger asyncUserLogger, AddressLogRepository addressLogRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.asyncUserLogger = asyncUserLogger;

        this.addressLogRepository = addressLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Address getUserAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
    }

    @Override
    @Transactional
    public void updateUserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);

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
        Address address = new Address(resident, true);
        addressRepository.save(address);
        asyncUserLogger.recordUserLog(resident.getUid(), UserLogType.ADDED_ADDRESS, "user added address");
        return address;
    }

    @Override
    @Transactional
    public void removeAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
        addressRepository.delete(address);
        asyncUserLogger.recordUserLog(user.getUid(), UserLogType.REMOVED_ADDRESS, "user deleted address");
        log.info("deleting user address from db");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAddress(String userUid) {
        final User user = userRepository.findOneByUid(userUid);
        final Address address = addressRepository.findTopByResidentAndPrimaryTrueOrderByCreatedDateTimeDesc(user);
        return address != null && address.hasHouseAndStreet();
    }

    @Override
    @Transactional
    public String storeAddressRaw(String userUid, Address address) {
        address.setPrimary(false); // to make sure we never accidentally override
        Address storedAddress;
        List<Address> duplicateCheck = addressRepository.findAll(Specifications
                        .where(forUser(address.getResident()))
                        .and(matchesStreetArea(address.getHouse(), address.getStreet(), address.getNeighbourhood())),
                new Sort(Sort.Direction.DESC, "createdDateTime"));
        log.info("size of returned address: {}", duplicateCheck.size());
        storedAddress = duplicateCheck.isEmpty() ?
                addressRepository.save(address) : duplicateCheck.iterator().next();
        return storedAddress.getUid();
    }

    @Override
    @Transactional
    public void confirmLocationAddress(String userUid, String addressUid,
                                       GeoLocation location, UserInterfaceType interfaceType) {
        User user = userRepository.findOneByUid(userUid);
        Address storedAddress = addressRepository.findOneByUid(addressUid);
        AddressLog log = new AddressLog.Builder()
                .address(storedAddress)
                .user(user)
                .type(AddressLogType.CONFIRMED_LOCATION)
                .location(location)
                .source(LocationSource.convertFromInterface(interfaceType))
                .description(null).build();
        addressLogRepository.save(log);
    }

    @Transactional
    public void reviseLocationAddress(String userUid, String addressUid, GeoLocation location,
                                      String description, UserInterfaceType interfaceType) {
        User user = userRepository.findOneByUid(userUid);
        Address storedAddress = addressRepository.findOneByUid(addressUid);
        AddressLog log = new AddressLog.Builder()
                .address(storedAddress)
                .user(user)
                .type(AddressLogType.REVISED_DESCRIPTION)
                .location(location)
                .source(LocationSource.convertFromInterface(interfaceType))
                .description(description).build();
        addressLogRepository.save(log);
    }
}