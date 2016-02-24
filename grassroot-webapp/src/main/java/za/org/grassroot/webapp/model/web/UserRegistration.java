package za.org.grassroot.webapp.model.web;

import org.hibernate.validator.constraints.NotBlank;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.AppIdGenerator;
import za.org.grassroot.core.util.PhoneNumberUtil;

/**
 * @author Lesetse Kimwaga
 */

public class UserRegistration {

    public User user;

    public UserRegistration() {
        user = new User(AppIdGenerator.generateId());
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @NotBlank(message = "First name is required!")
    public  String getFirstName()
    {
        return user.getFirstName();
    }

    @NotBlank(message = "Last name is required!")
    public String getLastName()
    {
        return  user.getLastName();
    }

    public void setLastName(String lastName)
    {
        user.setLastName(lastName);
    }

    @NotBlank(message = "{user.registration.validation.password.required}")
    public String getPassword() {
        return user.getPassword();
    }

    public void setPassword(String password) {
        user.setPassword(password);
    }

    @NotBlank(message = "{user.registration.validation.username.required}")
    public String getUsername() {
        return user.getUsername();
    }

    public void setUsername(String username) {
        user.setUsername(username);
    }

    @NotBlank(message = "Phone Number is required!")
    public String getPhoneNumber() {
        return user.getPhoneNumber();
    }

    public void setPhoneNumber(String phoneNumber) {
        String parsedPhoneNumber = PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        user.setPhoneNumber(parsedPhoneNumber);
    }
}
