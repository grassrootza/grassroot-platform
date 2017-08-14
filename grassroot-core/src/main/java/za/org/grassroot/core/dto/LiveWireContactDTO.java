package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.LiveWireContactType;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.Objects;

/**
 * Created by luke on 2017/05/15.
 */
public class LiveWireContactDTO {

    private String contactName;
    private String contactNumber;
    private String addressDescription;
    private Integer groupSize;
    private LiveWireContactType contactType;
    private String contactTypeDescription;

    public LiveWireContactDTO(User user) {
        this.contactName = user.getName();
        this.contactNumber = PhoneNumberUtil.invertPhoneNumber(user.getPhoneNumber());
        this.contactType = user.isLiveWireContact() ? LiveWireContactType.REGISTERED : LiveWireContactType.UNKNOWN;
        this.contactTypeDescription = typeEnumToString(contactType);
    }

    public LiveWireContactDTO(String contactName, String contactNumber, String addressDescription, Integer groupSize,
                              LiveWireContactType contactType) {
        this.contactName = contactName;
        this.contactNumber = contactNumber;
        this.addressDescription = addressDescription;
        this.groupSize = groupSize;
        this.contactType = contactType;
        this.contactTypeDescription = typeEnumToString(contactType);
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public String getAddressDescription() {
        return addressDescription;
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public LiveWireContactType getContactType() {
        return contactType;
    }

    public String getContactTypeDescription() {
        return contactTypeDescription == null ? typeEnumToString(contactType) : contactTypeDescription;
    }

    public boolean matchesFilter(String filterInLowerCase) {
        Objects.requireNonNull(filterInLowerCase);
        return (addressDescription != null && addressDescription.toLowerCase().contains(filterInLowerCase)) ||
                (contactName != null && contactName.toLowerCase().contains(filterInLowerCase)) ||
                (contactNumber != null && contactNumber.toLowerCase().contains(filterInLowerCase));
    }

    public void setAddressDescription(String addressDescription) {
        this.addressDescription = addressDescription;
    }

    public void setGroupSize(Integer groupSize) {
        this.groupSize = groupSize;
    }

    public void setContactType(LiveWireContactType contactType) {
        this.contactType = contactType;
    }

    // doing it here as client side rendering doesn't allow pulling in from messages file
    private static String typeEnumToString(LiveWireContactType contactType) {
        switch (contactType) {
            case REGISTERED:
                return "Signed up as LiveWire contact";
            case PUBLIC_MEETING:
                return "Called a public meeting";
            case UNKNOWN:
                return "Unknown";
            default:
                return "Unknown";
        }
    }
}
