package za.org.grassroot.webapp.controller.rest.group.join;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@ApiModel
@Getter @Setter @Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class JoinInfoExternal {

    @Builder.Default private boolean userLoggedIn = false;
    @Builder.Default private boolean userAlreadyMember = false;

    private String groupUid;
    private String groupName;
    private String groupDescription;
    private String groupImageUrl;

    private List<String> groupTopics;

}
