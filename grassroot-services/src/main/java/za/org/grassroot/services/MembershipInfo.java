package za.org.grassroot.services;

import za.org.grassroot.core.domain.User;

import java.util.Objects;

/**
 * Represents all info needed to add new member.
 * Only phone number is required.
 */
public class MembershipInfo {

    private final String phoneNumber;
    private final Long roleId;
    private final String displayName;

    public MembershipInfo(String phoneNumber, Long roleId, String displayName) {
        this.phoneNumber = Objects.requireNonNull(phoneNumber);
        this.roleId = roleId;
        this.displayName = displayName;
    }

    // constructor to create a membership info with an empty role
    public MembershipInfo(User user) {
        this.phoneNumber = user.getPhoneNumber();
        this.displayName = user.getDisplayName();
        this.roleId = null;
    }

    public static MembershipInfo makeEmpty() {
        return new MembershipInfo("", null, "");
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Long getRoleId() {
        return roleId;
    }

    public String getDisplayName() {
        return displayName;
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
        sb.append(", roleId=").append(roleId);
        sb.append(", displayName='").append(displayName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
