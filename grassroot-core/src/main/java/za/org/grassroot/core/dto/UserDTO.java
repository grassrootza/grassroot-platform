package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.User;

import java.io.Serializable;

/**
 * Created by aakilomar on 12/20/15.
 */
public class UserDTO implements Serializable {

    private boolean enabled;
    private String languageCode;
    private String lastName;
    private String firstName;
    private String displayName;
    private Long id;
    private String phoneNumber;


    public UserDTO() {
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.phoneNumber = user.getPhoneNumber();
        this.displayName =  user.getDisplayName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.languageCode = user.getLanguageCode();
        this.enabled = user.getEnabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public String toString() {
        return "UserDTO{" +
                "enabled=" + enabled +
                ", languageCode='" + languageCode + '\'' +
                ", lastName='" + lastName + '\'' +
                ", firstName='" + firstName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", id=" + id +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
