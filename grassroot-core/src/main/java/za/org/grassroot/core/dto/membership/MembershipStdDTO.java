package za.org.grassroot.core.dto.membership;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.Province;

import java.util.List;

// for big pulls, e.g., on filter
@Getter @JsonInclude(JsonInclude.Include.NON_NULL)
public class MembershipStdDTO {

    private final String userUid;
    private final String displayName;
    private final GroupRole roleName;
    private final Province province;
    private final String phoneNumber;
    private final String emailAddress;
    private final List<String> topics;
    private final List<String> affiliations;
    private final boolean canEditDetails;
    private final boolean contactError;

    public MembershipStdDTO(Membership membership) {
        User user = membership.getUser();
        this.userUid = user.getUid();
        this.displayName = membership.getDisplayName();
        this.roleName = membership.getRole();
        this.province = user.getProvince();
        this.phoneNumber = user.getPhoneNumber();
        this.emailAddress = user.getEmailAddress();
        this.topics = membership.getTopics();
        this.affiliations = membership.getAffiliations();
        this.canEditDetails = !(user.hasPassword() || user.isHasSetOwnName());
        this.contactError = user.isContactError();
    }

    public boolean hasEmail() {
        return !StringUtils.isEmpty(emailAddress);
    }

    public boolean hasPhone() {
        return !StringUtils.isEmpty(phoneNumber);
    }

    public boolean hasBoth() {
        return hasEmail() && hasPhone();
    }

}
