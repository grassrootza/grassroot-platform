package za.org.grassroot.core.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

@Getter @Setter @NoArgsConstructor
public class UserFullDTO {

    private String uid;
    private String displayName;
    private String phoneNumber;
    private String email;
    private String lastName;
    private String firstName;
    private boolean enabled;
    private String languageCode;
    private Province province;
    private boolean hasPassword;
    private boolean hasImage;
    private boolean contactError;

    public UserFullDTO(User user) {
        this.phoneNumber = user.getPhoneNumber();
        this.displayName = user.getName();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.languageCode = user.getLocale().getLanguage();
        this.enabled = user.isEnabled();
        this.uid = user.getUid();
        this.email = user.getEmailAddress();
        this.province = user.getProvince();
        this.hasPassword = user.hasPassword();
        this.hasImage = user.isHasImage();
        this.contactError = user.isContactError();
    }

}
