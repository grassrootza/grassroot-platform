package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import java.util.Optional;

public class AndroidAuthToken {

    private String userUid;
    private String msisdn;
    private String displayName;
    private String systemRoleName;

    private String token;

    public AndroidAuthToken(User user, String token) {
        this.userUid = user.getUid();
        this.msisdn = user.getPhoneNumber();
        this.displayName = user.getDisplayName();

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

    public String getSystemRoleName() {
        return systemRoleName;
    }
}
