package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Getter
public class AuthorizedUserDTO {
    private static final Comparator<StandardRole> SYSTEM_ROLE_COMPARATOR = (role, t1) -> {
        if (role.equals(t1)) {
            return 0;
        }

        switch (role) {
            case ROLE_SYSTEM_ADMIN:
                return 1;
            case ROLE_ACCOUNT_ADMIN: // we know it's not equal or null
                return t1.equals(StandardRole.ROLE_SYSTEM_ADMIN) ? -1 : 1;
            case ROLE_FULL_USER: // we know it's not equal, and t1 is not null, so must be greater
                return -1;
            default: // should never happen, but in case something strange added in future, put it last
                return -1;
        }
    };

    private String userUid;
    private String msisdn;
    private String displayName;
    private String email;
    private String languageCode;
    private String systemRoleName;
    private Province province;
    private boolean hasImage;
    private String token;
    private List<StandardRole> systemRoles;
    private boolean hasAccount;
    private boolean whatsAppOptedIn;

    public AuthorizedUserDTO(User user, String token) {
        this.userUid = user.getUid();
        this.msisdn = user.getPhoneNumber();
        this.displayName = user.getDisplayName();
        this.languageCode = user.getLocale().getLanguage();
        this.email = user.getEmailAddress();
        this.province = user.getProvince();
        this.hasImage = user.isHasImage();
        this.whatsAppOptedIn = user.isWhatsAppOptedIn();

        Optional<StandardRole> highestSystemRole = user.getStandardRoles().stream().max(SYSTEM_ROLE_COMPARATOR);
        systemRoles = new ArrayList<>(user.getStandardRoles());

        this.systemRoleName = highestSystemRole.isPresent() ? highestSystemRole.get().name() : "";
        this.token = token;

        this.hasAccount = user.getPrimaryAccount() != null
                && !user.getPrimaryAccount().isClosed()
                && user.getPrimaryAccount().isEnabled();

        this.whatsAppOptedIn = user.isWhatsAppOptedIn();
    }

}