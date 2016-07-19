package za.org.grassroot.services.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.AddressRepository;
import za.org.grassroot.services.AddressBroker;
import za.org.grassroot.services.UserManagementService;

import java.util.Objects;

/**
 * Created by paballo on 2016/07/14.
 */
@Service
public class AddressBrokerImpl implements AddressBroker {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private AddressRepository addressRepository;


    @Override
    public Address getUserAddress(String userUid) {

        Objects.requireNonNull(userUid);
        User user  = userManagementService.load(userUid);
        return  addressRepository.findOneByResident(user);
    }

    @Override
    public void adduserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);
        User user  = userManagementService.load(userUid);

        Address address = new Address(user,houseNumber,street,town);

        addressRepository.save(address);
    }

    @Override
    public void updateUserAddress(String userUid, String houseNumber, String street, String town) {
        Objects.requireNonNull(userUid);
        User user  = userManagementService.load(userUid);

        Address address = addressRepository.findOneByResident(user);
        address.setHouseNumber(houseNumber);
        address.setStreetName(street);
        address.setTown(town);

        addressRepository.save(address);

    }

    @Override
    public void updateHouseNumber(String userUid, String houseNumber) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(houseNumber);
        User user  = userManagementService.load(userUid);

        Address address = addressRepository.findOneByResident(user);
        address.setHouseNumber(houseNumber);

        addressRepository.save(address);



    }

    @Override
    public void updateStreet(String userUid, String street) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(street);
        User user  = userManagementService.load(userUid);

        Address address = addressRepository.findOneByResident(user);
        address.setStreetName(street);

        addressRepository.save(address);

    }

    @Override
    public void updateTown(String userUid, String town) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(town);
        User user  = userManagementService.load(userUid);

        Address address = addressRepository.findOneByResident(user);
        address.setTown(town);

        addressRepository.save(address);

    }

    @Override
    public void removeAddress(String userUid) {
        Objects.requireNonNull(userUid);
        User user  = userManagementService.load(userUid);
        Address address = addressRepository.findOneByResident(user);

        addressRepository.delete(address);

    }

    @Override
    public boolean addressExists(String userUid) {
        User user = userManagementService.load(userUid);

        return addressRepository.findOneByResident(user) !=null;
    }
}
