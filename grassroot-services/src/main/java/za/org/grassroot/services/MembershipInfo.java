package za.org.grassroot.services;

import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents all info needed to add new member.
 * Only phone number is required.
 */
public class MembershipInfo {

    private final String phoneNumber;
    private final String roleName; // optional
    private final String displayName; // optional

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
}
