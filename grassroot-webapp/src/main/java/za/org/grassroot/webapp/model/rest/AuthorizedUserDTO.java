package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.Optional;

public class AuthorizedUserDTO {

    private String userUid;
    private String msisdn;
    private String displayName;
    private String email;
    private String languageCode;
    private String systemRoleName;
    private String token;

    public AuthorizedUserDTO(User user, String token) {
        this.userUid = user.getUid();
        this.msisdn = user.getPhoneNumber();
        this.displayName = user.getDisplayName();
        this.languageCode = user.getLanguageCode();
        this.email = user.getEmailAddress();

        Optional<Role> highestSystemRole = user.getStandardRoles().stream()
                .sorted(BaseRoles.sortSystemRole.reversed())
                .findFirst();

        this.systemRoleName = highestSystemRole.isPresent() ? highestSystemRole.get().getName() : "";
        this.token = token;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getToken() {
        return token;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getEmail() {
        return email;
    }

    public String getSystemRoleName() {
        return systemRoleName;
    }
}