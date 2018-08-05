package za.org.grassroot.webapp.controller.rest.group;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.org.grassroot.core.dto.membership.MembershipStdDTO;

import java.util.List;

@ApiModel @Getter @Setter @NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupFilterResponse {

    private List<MembershipStdDTO> content;
    private long totalElements;

    private long numberSms;
    private long numberEmail;
    private long numberSmsAndEmail;

}
