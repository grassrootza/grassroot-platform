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

    public LiveWireContactDTO(User user) {
        this.contactName = user.getName();
        this.contactNumber = PhoneNumberUtil.invertPhoneNumber(user.getPhoneNumber());
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
}
