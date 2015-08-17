package za.org.grassroot.webapp.model.web;

import org.hibernate.validator.constraints.NotBlank;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import za.org.grassroot.core.domain.User;

/**
 * @author Lesetse Kimwaga
 */

public class UserRegistration {


    public User user;

    private PasswordEncoder passwordEncoder =  new BCryptPasswordEncoder();


    public UserRegistration() {
        user = new User();
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

    @NotBlank(message = "{user.registration.validation.password.required}")
    public String getPassword() {
        return user.getPassword();
    }

    public void setPassword(String password) {


        user.setPassword(password);
    }

    public String getUsername() {
        return user.getUsername();
    }

    public void setUsername(String username) {
        user.setUsername(passwordEncoder.encode(username));
    }

    public String getPhoneNumber() {
        return user.getPhoneNumber();
    }

    public void setPhoneNumber(String phoneNumber) {
        user.setPhoneNumber(phoneNumber);
    }
}
