package za.org.grassroot.core.dto;

import org.springframework.beans.factory.annotation.Value;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.Arrays;

/**
 * Created by luke on 2016/02/04.
 */
public class MaskedUserDTO {

    private static int phoneNumberLength;

    private final char maskingCharacter = '*';

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

    public static void setPhoneNumberLength(int msisdnLength) {
        phoneNumberLength = msisdnLength;
    }

    // requiring the pass in otherwise have to wire this up as a component just to get the property size
    public MaskedUserDTO(User user) {
        this.id = user.getId();
        this.uid = user.getUid();
        this.firstName = maskName(user.getFirstName());
        this.lastName = maskName(user.getLastName());
        this.displayName = maskName(user.nameToDisplay());
        this.phoneNumber = maskPhoneNumber(user.getPhoneNumber());
        this.languageCode = user.getLanguageCode();
        this.createdDateTime = user.getCreatedDateTime();

        //this.hasInitiatedSession = user.isHasInitiatedSession();
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

    private String maskPhoneNumber(String msisdn) {
        char[] chars = new char[phoneNumberLength - 6];
        Arrays.fill(chars, maskingCharacter);
        return msisdn.substring(0,2) + (new String(chars)) + msisdn.substring(8, phoneNumberLength);
    }

    private String maskName(String name) {
        if (name == null || name.trim().equals("")) return name;
        int lengthOfMask = name.length() - 1;
        char[] mask = new char[lengthOfMask];
        Arrays.fill(mask, maskingCharacter);
        return name.substring(0, 1) + (new String(mask));
    }


}
