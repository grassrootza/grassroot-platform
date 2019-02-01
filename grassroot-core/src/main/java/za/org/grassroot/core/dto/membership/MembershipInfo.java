package za.org.grassroot.core.dto.membership;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.RoleName;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;

import java.util.*;

/**
 * Represents all info needed to add new member.
 * Only phone number is required.
 */
@ApiModel(value = "MembershipInfo", description = "Set of information, principally name, phone number and/or email")
@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MembershipInfo implements Comparable<MembershipInfo> {

    // note: removing 'final' so Thymeleaf can populate this (can find a better way if needed)
    protected String userUid; // for optionality
    protected String phoneNumber;

    @ApiModelProperty(allowEmptyValue = true)
    protected RoleName roleName; // optional

    protected String displayName; // optional
    protected String firstName;
    protected String surname;
    protected boolean userSetName;

    protected String memberEmail;
    protected Province province;

    protected List<String> topics;
    protected List<String> affiliations;
    protected List<String> taskTeams;

    public MembershipInfo() {
        // need empty constructor for Spring MVC form submission, and for the Excel parsing
    }

    @JsonCreator
    public MembershipInfo(@JsonProperty("phoneNumber") String phoneNumber,
                          @JsonProperty("roleName") RoleName roleName,
                          @JsonProperty("displayName") String displayName) {
        this.phoneNumber = phoneNumber;
        this.roleName = roleName;
        this.displayName = displayName;
        this.userSetName = false; // since only true if user themselves set the name recorded in the info entity
    }

    // constructor to create a membership info with an empty role
    public MembershipInfo(User user, String displayName, RoleName roleName, List<String> assignedTopics) {
        this.phoneNumber = user.getPhoneNumber();
        this.memberEmail = user.getEmailAddress();
        this.displayName = displayName;
        this.userSetName = user.isHasSetOwnName();
        this.roleName = roleName;
        this.userUid = user.getUid();
        this.province = user.getProvince();
        this.topics = assignedTopics == null ? new ArrayList<>() : assignedTopics;
    }

    public static MembershipInfo makeEmpty() {
        return new MembershipInfo("", null, "");
    }

    public static Set<MembershipInfo> createFromMembers(Set<Membership> members) {
        Set<MembershipInfo> membershipInfoSet = new HashSet<>();
        members.forEach(m -> membershipInfoSet.add(new MembershipInfo(m.getUser(), m.getDisplayName(), m.getRole(),
                m.getTopics())));
        return membershipInfoSet;
    }

    // need to use PhoneNumberUtil here to make sure return number with country code (or vice versa)
    public String getPhoneNumberWithCCode() {
        try {
            return PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        }  catch (InvalidPhoneNumberException e) {
            return phoneNumber;
        }
    }

    public Optional<String> getConvertedNumber() {
        return StringUtils.isEmpty(phoneNumber) ? Optional.empty() : Optional.of(getPhoneNumberWithCCode());
    }

    public String getPhoneNumberWithoutCCode() {
        try {
            return PhoneNumberUtil.invertPhoneNumber(phoneNumber);
        } catch (Exception e) {
            return phoneNumber;
        }
    }

    public String getNationalFormattedNumber() {
        if (phoneNumber != null) {
            try {
                return PhoneNumberUtil.formattedNumber(phoneNumber);
            } catch (Exception e) {
                return phoneNumber;
            }
        } else {
            return null;
        }
    }

    public boolean hasPhoneNumber() {
        return !StringUtils.isEmpty(phoneNumber);
    }

    public boolean hasValidPhoneNumber() {
        if (StringUtils.isEmpty(phoneNumber)) {
            return false;
        }

        try {
            PhoneNumberUtil.convertPhoneNumber(phoneNumber);
            return true;
        } catch (InvalidPhoneNumberException e) {
            return false;
        }
    }

    public boolean hasValidEmail() {
        return !StringUtils.isEmpty(memberEmail) && EmailValidator.getInstance().isValid(memberEmail);
    }

    public Optional<String> getFormattedEmail() {
        return StringUtils.isEmpty(memberEmail) ? Optional.empty() : Optional.of(memberEmail.trim().toLowerCase());
    }

    public boolean hasValidPhoneOrEmail() {
        return hasValidPhoneNumber() || hasValidEmail();
    }

    public boolean hasTaskTeams() {
        return taskTeams != null && !taskTeams.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MembershipInfo that = (MembershipInfo) o;
        return Objects.equals(userUid, that.userUid) &&
                Objects.equals(phoneNumber, that.phoneNumber) &&
                Objects.equals(memberEmail, that.memberEmail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userUid, phoneNumber, memberEmail);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MembershipInfo{");
        sb.append("province=").append(province);
        sb.append(", phoneNumber='").append(phoneNumber).append('\'');
        sb.append(", email='").append(memberEmail).append('\'');
        sb.append(", roleName=").append(roleName);
        sb.append(", displayName='").append(displayName).append('\'');
        sb.append(", topics='").append(topics).append('\'');
        sb.append('}');
        return sb.toString();
    }


    @Override
    public int compareTo(MembershipInfo m) {
        RoleName otherRole = m.getRoleName();
        if (roleName != null && !roleName.equals(otherRole)) {
            return roleName.compareTo(otherRole);
        } else {
            return StringUtils.isEmpty(displayName) ? -1 :
                    StringUtils.isEmpty(m.getDisplayName()) ? 1 :
                            displayName.compareTo(m.getDisplayName());
        }
    }
}
