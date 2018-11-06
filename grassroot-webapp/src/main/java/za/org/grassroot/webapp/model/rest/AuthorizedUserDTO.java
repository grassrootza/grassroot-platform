package za.org.grassroot.webapp.model.rest;

import lombok.Getter;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.Province;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private List<String> systemRoles;
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

        Optional<Role> highestSystemRole = user.getStandardRoles().stream().max(BaseRoles.sortSystemRole);
        systemRoles = user.getStandardRoles().stream().map(Role::getName).collect(Collectors.toList());

        this.systemRoleName = highestSystemRole.isPresent() ? highestSystemRole.get().getName() : "";
        this.token = token;

        this.hasAccount = user.getPrimaryAccount() != null
                && !user.getPrimaryAccount().isClosed()
                && user.getPrimaryAccount().isEnabled();

        this.whatsAppOptedIn = user.isWhatsAppOptedIn();
    }

}