package za.org.grassroot.webapp.controller.rest.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.Province;

import java.util.List;

@ApiModel
@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddMemberInfo {

    @ApiModelProperty(notes = "Must be in the 'msisdn' format, i.e., 2761....")
    private String memberMsisdn;
    private String displayName;

    @ApiModelProperty(notes = "Can be empty, in which case defaults to ROLE_ORDINARY_MEMBER, for list of possible roles" +
            "see BaseRoles class")
    private String roleName;

    @ApiModelProperty(notes = "Can be empty / null")
    private List<String> alernateNumbers;

    @ApiModelProperty(notes = "Can be null, but should only be one")
    private String emailAddress;

    @ApiModelProperty(notes = "Can be empty")
    private Province province;

    @ApiModelProperty(notes = "Can be empty")
    private List<String> topics;

    @ApiModelProperty(notes = "Can be empty")
    private List<String> taskTeams;

    @ApiModelProperty(notes = "Can be empty")
    private List<String> affiliations;

    protected MembershipInfo convertToMembershipInfo() {
        MembershipInfo info = new MembershipInfo();
        info.setDisplayName(displayName);
        info.setPhoneNumber(memberMsisdn);
        info.setMemberEmail(emailAddress);
        info.setRoleName(StringUtils.isEmpty(roleName) ? BaseRoles.ROLE_ORDINARY_MEMBER : roleName);
        info.setProvince(province);
        info.setTopics(topics != null && !topics.isEmpty() ? topics : null);
        info.setAffiliations(affiliations != null && !affiliations.isEmpty() ? affiliations : null);
        return info;
    }

    public AddMemberInfo(MembershipInfo membershipInfo) {
        this.memberMsisdn = membershipInfo.getPhoneNumber();
        this.displayName = membershipInfo.getDisplayName();
        this.roleName = membershipInfo.getRoleName();
        this.emailAddress = membershipInfo.getMemberEmail();
        this.province = membershipInfo.getProvince();
        this.topics = membershipInfo.getTopics();
        this.affiliations = membershipInfo.getAffiliations();
    }

}
