package za.org.grassroot.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserCreateRequest;

import java.io.Serializable;

/**
 * Created by aakilomar on 12/20/15.
 */
@Getter @Setter
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
        this.languageCode = user.getLocale().getLanguage();
        this.enabled = user.isEnabled();
        this.id = user.getUid();
    }

    public UserDTO(UserCreateRequest userCreateRequest){
        this.phoneNumber = userCreateRequest.getPhoneNumber();
        this.displayName=userCreateRequest.getDisplayName();
        this.password = userCreateRequest.getPassword();
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
