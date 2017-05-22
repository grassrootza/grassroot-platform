package za.org.grassroot.services.user;

import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.UserInterfaceType;

/**
 * Created by paballo on 2016/07/14.
 */
public interface AddressBroker {

    Address getUserAddress(String userUid);

    void updateUserAddress(String userUid, String houseNumber, String street, String town);

    void removeAddress(String userUid);

    boolean hasAddress(String userUid);

    String storeAddressRaw(String userUid, Address address);

    void confirmLocationAddress(String userUid, String addressUid,
                                GeoLocation location, UserInterfaceType interfaceType);

    void reviseLocationAddress(String userUid, String addressUid, GeoLocation location,
                               String description, UserInterfaceType interfaceType);
}
