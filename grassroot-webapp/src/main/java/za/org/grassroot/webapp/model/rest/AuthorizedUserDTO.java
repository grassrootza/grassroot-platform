package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.RoleName;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class AuthorizedUserDTO {
    private static final Comparator<Role> SYSTEM_ROLE_COMPARATOR = (role, t1) -> {
        if (StringUtils.isEmpty(role.getName())) {
            return StringUtils.isEmpty(t1.getName()) ? 0 : -1;
        }

        if (role.getName().equals(t1.getName())) {
            return 0;
        }

        if (StringUtils.isEmpty(t1.getName())) {
            // we know role is not null so by definition it is greater
            return 1;
        }

        switch (role.getName()) {
            case ROLE_SYSTEM_ADMIN:
                return 1;
            case ROLE_ACCOUNT_ADMIN: // we know it's not equal or null
                return t1.getName().equals(RoleName.ROLE_SYSTEM_ADMIN) ? -1 : 1;
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
    private List<RoleName> systemRoles;
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

        Optional<Role> highestSystemRole = user.getStandardRoles().stream().max(SYSTEM_ROLE_COMPARATOR);
        systemRoles = user.getStandardRoles().stream().map(Role::getName).collect(Collectors.toList());

        this.systemRoleName = highestSystemRole.isPresent() ? highestSystemRole.get().getName().name() : "";
        this.token = token;

        this.hasAccount = user.getPrimaryAccount() != null
                && !user.getPrimaryAccount().isClosed()
                && user.getPrimaryAccount().isEnabled();

        this.whatsAppOptedIn = user.isWhatsAppOptedIn();
    }

}