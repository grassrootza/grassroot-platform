package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MemberWrapper implements Comparable<MemberWrapper> {

    private static final Logger log = LoggerFactory.getLogger(MemberWrapper.class);

    private String memberUid;
    private String displayName;
    private String roleName;
    private String phoneNumber;

    private boolean changed;
    private boolean added;
    private boolean deleted;

    private boolean currentUser;

    public MemberWrapper(Membership membership, User currentUser) {
        this.memberUid = membership.getUser().getUid();
        this.displayName = membership.getUser().getDisplayName();
        this.roleName = membership.getRole().getName();
        this.phoneNumber = PhoneNumberUtil.invertPhoneNumber(membership.getUser().getPhoneNumber());

        this.changed = false;
        this.added = false;
        this.deleted = false;

        this.currentUser = membership.getUser().equals(currentUser);
    }

    public MemberWrapper() {
        // for Thymeleaf
    }

    public static MembershipInfo convertToMemberInfo(MemberWrapper wrapper) {
        return new MembershipInfo(PhoneNumberUtil.convertPhoneNumber(wrapper.phoneNumber),
                wrapper.roleName, wrapper.displayName);
    }

    public static List<MemberWrapper> generateListFromGroup(Group group, User user) {
        return group.getMemberships().stream()
                .map(m -> new MemberWrapper(m, user))
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    public static Logger getLog() {
        return log;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }

    public boolean isAdded() {
        return added;
    }

    public void setAdded(boolean added) {
        this.added = added;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(boolean currentUser) {
        this.currentUser = currentUser;
    }

    public String getMemberUid() {
        return memberUid;
    }

    public void setMemberUid(String memberUid) {
        this.memberUid = memberUid;
    }

    public boolean isNonNull() {
        return !StringUtils.isEmpty(phoneNumber);
    }

    @Override
    public int compareTo(MemberWrapper wrapper) {
        String otherRole = wrapper.getRoleName();
        if (!StringUtils.isEmpty(roleName) && !roleName.equals(otherRole)) {
            return Role.compareRoleNames(roleName, otherRole);
        } else {
            return StringUtils.isEmpty(displayName) ? -1 :
                    StringUtils.isEmpty(wrapper.getDisplayName()) ? 1 :
                            displayName.compareTo(wrapper.getDisplayName());
        }
    }

    @Override
    public String toString() {
        return "MemberWrapper{" +
                ", displayName='" + displayName + '\'' +
                ", roleName='" + roleName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
