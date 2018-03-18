package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

import java.util.Optional;

@Getter
public class AuthorizedUserDTO {

    private String userUid;
    private String msisdn;
    private String displayName;
    private String email;
    private String languageCode;
    private String systemRoleName;
    private Province province;
    private boolean hasImage;
    private String token;

    public AuthorizedUserDTO(User user, String token) {
        this.userUid = user.getUid();
        this.msisdn = user.getPhoneNumber();
        this.displayName = user.getDisplayName();
        this.languageCode = user.getLocale().getLanguage();
        this.email = user.getEmailAddress();
        this.province = user.getProvince();
        this.hasImage = user.isHasImage();

        Optional<Role> highestSystemRole = user.getStandardRoles().stream().max(BaseRoles.sortSystemRole);

        this.systemRoleName = highestSystemRole.isPresent() ? highestSystemRole.get().getName() : "";
        this.token = token;
    }

}