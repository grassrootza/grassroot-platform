package za.org.grassroot.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserCreateRequest;

import java.io.Serializable;

/**
 * Created by aakilomar on 12/20/15.
 */
public class UserDTO implements Serializable {


    private String id;
    @JsonIgnore
    private boolean enabled;
    @JsonIgnore
    private String languageCode;
    @JsonIgnore
    private String lastName;
    @JsonIgnore
    private String firstName;

    private String displayName;
    private String phoneNumber;

    @JsonIgnore
    private String password;

    public UserDTO() {
    }

    public UserDTO(User user) {
        this.phoneNumber = user.getPhoneNumber();
        this.displayName =  user.getDisplayName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.languageCode = user.getLanguageCode();
        this.enabled = user.isEnabled();
        this.id = user.getUid();
    }

    public UserDTO(String phoneNumber, String displayName){
        this.phoneNumber = phoneNumber;
        this.displayName=displayName;
    }

    public UserDTO(UserCreateRequest userCreateRequest){
        this.phoneNumber = userCreateRequest.getPhoneNumber();
        this.displayName=userCreateRequest.getDisplayName();
        this.password = userCreateRequest.getPassword();
    }

    public UserDTO(Object[] objArray) {

        this.displayName = String.valueOf(objArray[1]);
        this.phoneNumber =String.valueOf(objArray[2]);
        this.languageCode =String.valueOf(objArray[3]);

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


    public String getId() {
        return id;
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
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
