package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.MaskingUtil;

import java.time.Instant;

/**
 * Created by luke on 2016/02/04.
 */
public class MaskedUserDTO {

    private Long id;
    private String uid;
    private String firstName;
    private String lastName;
    private String displayName;
    private String phoneNumber;
    private String languageCode;
    private Instant createdDateTime;

    private boolean hasInitiatedSession;
    private boolean webProfile;
    private boolean enabled;

    public MaskedUserDTO(User user) {
        this.id = user.getId();
        this.uid = user.getUid();
        this.firstName = MaskingUtil.maskName(user.getFirstName());
        this.lastName = MaskingUtil.maskName(user.getLastName());
        this.displayName = MaskingUtil.maskName(user.getDisplayName());
        this.phoneNumber = MaskingUtil.maskPhoneNumber(user.getPhoneNumber());
        this.languageCode = user.getLanguageCode();
        this.createdDateTime = user.getCreatedDateTime();

        this.hasInitiatedSession = user.isHasInitiatedSession();
        this.webProfile = user.isHasWebProfile();
        this.enabled = user.isEnabled();
    }

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getDisplayName() { return displayName; }

    public String getLanguageCode() {
        return languageCode;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public boolean isHasInitiatedSession() {
        return hasInitiatedSession;
    }

    public boolean isWebProfile() {
        return webProfile;
    }

    public boolean isEnabled() {
        return enabled;
    }

}
