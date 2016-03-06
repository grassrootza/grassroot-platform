package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.User;

import java.io.Serializable;

/**
 * Created by aakilomar on 9/5/15.
 */
public class UserDTO implements Serializable {

    private Long id;
    private String phoneNumber;
    private String displayName;

    public UserDTO(User user) {
        this.id = user.getId();
        this.phoneNumber = user.getPhoneNumber();
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
                "id=" + id +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
