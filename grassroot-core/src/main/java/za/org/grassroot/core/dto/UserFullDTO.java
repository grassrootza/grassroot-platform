package za.org.grassroot.core.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.org.grassroot.core.domain.User;


@Getter
@Setter
@NoArgsConstructor
public class UserFullDTO {

    private String uid;

    private String displayName;

    private String phoneNumber;

    private String email;

    private String lastName;

    private String firstName;

    private boolean enabled;

    private String languageCode;


    public UserFullDTO(User user) {

        this.phoneNumber = user.getPhoneNumber();
        this.displayName = user.getDisplayName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.languageCode = user.getLanguageCode();
        this.enabled = user.isEnabled();
        this.uid = user.getUid();
        this.email = user.getEmailAddress();
    }


}
