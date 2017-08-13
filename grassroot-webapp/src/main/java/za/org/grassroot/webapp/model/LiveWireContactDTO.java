package za.org.grassroot.webapp.model;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;

/**
 * Created by luke on 2017/05/15.
 */
public class LiveWireContactDTO {

    // todo: add group size etc
    private String contactName;
    private String contactNumber;
    private String addressDescription;
    private Integer groupSize;

    public LiveWireContactDTO(User user) {
        this.contactName = user.getName();
        this.contactNumber = PhoneNumberUtil.invertPhoneNumber(user.getPhoneNumber());
    }

    public LiveWireContactDTO(String contactName, String contactNumber, String addressDescription, Integer groupSize) {
        this.contactName = contactName;
        this.contactNumber = contactNumber;
        this.addressDescription = addressDescription;
        this.groupSize = groupSize;
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
}
