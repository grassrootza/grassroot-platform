package za.org.grassroot.services;

import com.fasterxml.jackson.annotation.JsonCreator;
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
public class MembershipInfo implements Comparable<MembershipInfo> {

    // note: removing 'final' so Thymeleaf can populate this (can find a better way if needed)
    private String phoneNumber;
    private String roleName; // optional
    private String displayName; // optional

    @JsonCreator
    public MembershipInfo(@JsonProperty("phoneNumber") String phoneNumber, @JsonProperty("roleName") String roleName,
                          @JsonProperty("displayName") String displayName) {
        this.phoneNumber = Objects.requireNonNull(phoneNumber);
        this.roleName = roleName;
        this.displayName = displayName;
    }

    // constructor to create a membership info with an empty role
    public MembershipInfo(User user) {
        this.phoneNumber = user.getPhoneNumber();
        this.displayName = user.getDisplayName();
        this.roleName = null;
    }

    public static MembershipInfo makeEmpty() {
        return new MembershipInfo("", null, "");
    }

    public static Set<MembershipInfo> createFromMembers(Set<Membership> members) {
        Set<MembershipInfo> membershipInfoSet = new HashSet<>();
        for (Membership member : members)
            membershipInfoSet.add(new MembershipInfo(member.getUser().getPhoneNumber(), member.getRole().getName(),
                                                     member.getUser().getDisplayName()));
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

        if (!phoneNumber.equals(that.phoneNumber)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return phoneNumber.hashCode();
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
