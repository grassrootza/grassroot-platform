package za.org.grassroot.webapp.controller.rest.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@ApiModel
@Getter @Setter @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupJoinInfo {

    @Builder.Default private boolean userLoggedIn = false;
    @Builder.Default private boolean userAlreadyMember = false;

    private String groupUid;
    private String groupName;
    private String groupDescription;

    private List<String> groupTopics;

}
