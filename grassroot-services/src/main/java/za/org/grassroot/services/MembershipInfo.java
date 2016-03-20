package za.org.grassroot.services;

import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
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

    public MembershipInfo(String phoneNumber, String roleName, String displayName) {
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

    /* Logic here:
    If the role names are the same, they are equal
    If they are not, and this one is ordinary member, then it is always "less than" the other
    If it is not ordinary member, and they are not equal, the only case where it is "less than" is when it is committee
    member and the other is organizer
     */
    @Override
    public int compareTo(MembershipInfo m) {
        String otherRole = m.getRoleName();
        if (roleName.equals(otherRole)) {
            String otherName = m.getDisplayName();
            if (displayName == null ^ otherName == null)
                return (displayName == null) ? -1 : 1;
            if (displayName == null && otherName == null)
                return phoneNumber.compareTo(m.getPhoneNumber()); // todo: check how this works ...
            return displayName.compareToIgnoreCase(otherName);
        } else if (roleName.equals(BaseRoles.ROLE_ORDINARY_MEMBER)) {
            return -1;
        } else if (roleName.equals(BaseRoles.ROLE_COMMITTEE_MEMBER) && otherRole.equals(BaseRoles.ROLE_GROUP_ORGANIZER)) {
            return -1;
        } else {
            return 1;
        }
    }
}
