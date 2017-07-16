package za.org.grassroot.core.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents all info needed to add new member.
 * Only phone number is required.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MembershipInfo implements Comparable<MembershipInfo> {

    // note: removing 'final' so Thymeleaf can populate this (can find a better way if needed)
    private String phoneNumber;
    private String roleName; // optional
    private String displayName; // optional
    private boolean userSetName;

    public MembershipInfo() {
        // need empty constructor for Spring MVC form submission
    }

    @JsonCreator
    public MembershipInfo(@JsonProperty("phoneNumber") String phoneNumber, @JsonProperty("roleName") String roleName,
                          @JsonProperty("displayName") String displayName) {
        this.phoneNumber = Objects.requireNonNull(phoneNumber);
        this.roleName = roleName;
        this.displayName = displayName;
        this.userSetName = false; // since only true if user themselves set the name recorded in the info entity
    }

    // constructor to create a membership info with an empty role
    public MembershipInfo(User user, String displayName, String roleName) {
        this.phoneNumber = user.getPhoneNumber();
        this.displayName = displayName;
        this.userSetName = user.isHasSetOwnName();
        this.roleName = roleName;
    }

    public static MembershipInfo makeEmpty() {
        return new MembershipInfo("", null, "");
    }

    public static Set<MembershipInfo> createFromMembers(Set<Membership> members) {
        Set<MembershipInfo> membershipInfoSet = new HashSet<>();
        members.forEach(m -> membershipInfoSet.add(new MembershipInfo(m.getUser(), m.getDisplayName(), m.getRole().getName())));
        return membershipInfoSet;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDisplayName() {
        return displayName;
    }

    // need to add setters so that Thymeleaf can fill the entities

    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public void setRoleName(String roleName) { this.roleName = roleName; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isUserSetName() {
        return userSetName;
    }

    public void setUserSetName(boolean userSetName) {
        this.userSetName = userSetName;
    }

    // need to use PhoneNumberUtil here to make sure return number with country code (or vice versa)

    public String getPhoneNumberWithCCode() { return PhoneNumberUtil.convertPhoneNumber(phoneNumber); }

    public String getPhoneNumberWithoutCCode() { return PhoneNumberUtil.invertPhoneNumber(phoneNumber); }

    public String getNationalFormattedNumber() { return PhoneNumberUtil.formattedNumber(phoneNumber); }

    public boolean hasValidPhoneNumber() {
        try {
            getPhoneNumberWithCCode();
            return true;
        } catch (InvalidPhoneNumberException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MembershipInfo that = (MembershipInfo) o;

        return phoneNumber.equals(that.phoneNumber);

    }

    @Override
    public int hashCode() {
        return phoneNumber == null ? 0 : phoneNumber.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MembershipInfo{");
        sb.append("phoneNumber='").append(phoneNumber).append('\'');
        sb.append(", roleName=").append(roleName);
        sb.append(", displayName='").append(displayName).append('\'');
        sb.append('}');
        return sb.toString();
    }


    @Override
    public int compareTo(MembershipInfo m) {
        String otherRole = m.getRoleName();
        if (!StringUtils.isEmpty(roleName) && !roleName.equals(otherRole)) {
            return Role.compareRoleNames(roleName, otherRole);
        } else {
            return StringUtils.isEmpty(displayName) ? -1 :
                    StringUtils.isEmpty(m.getDisplayName()) ? 1 :
                            displayName.compareTo(m.getDisplayName());
        }
    }
}
