package za.org.grassroot.webapp.model.web;

import org.hibernate.validator.constraints.NotBlank;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.PhoneNumberUtil;

/**
 * @author Lesetse Kimwaga
 */

public class UserRegistration {

    public User user;

    public UserRegistration() {
        user = User.makeEmpty();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @NotBlank(message = "{user.registration.validation.displayname.required}")
    public String getDisplayName() { return user.getDisplayName(); }

    public void setDisplayName(String displayName) { user.setDisplayName(displayName); }

    @NotBlank(message = "{user.registration.validation.password.required}")
    public String getPassword() {
        return user.getPassword();
    }

    public void setPassword(String password) {
        user.setPassword(password);
    }

    // @NotBlank(message = "{user.registration.validation.username.required}")
    public String getUsername() {
        return user.getUsername();
    }

    public void setUsername(String username) {
        user.setUsername(username);
    }

    // @NotBlank(message = "Phone Number is required!")
    public String getPhoneNumber() {
        return user.getPhoneNumber();
    }

    public void setPhoneNumber(String phoneNumber) {
        String parsedPhoneNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        user.setPhoneNumber(parsedPhoneNumber);
    }

    public String getEmailAddress() { return user.getEmailAddress(); }

    public void setEmailAddress(String emailAddress) {
        user.setEmailAddress(emailAddress);
    }
}
