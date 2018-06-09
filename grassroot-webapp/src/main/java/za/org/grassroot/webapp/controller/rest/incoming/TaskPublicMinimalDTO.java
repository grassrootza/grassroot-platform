package za.org.grassroot.webapp.controller.rest.incoming;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.task.*;

import java.util.List;

@Getter @JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TaskPublicMinimalDTO {

    private String createdByUserName;
    private String title;
    private long deadlineMillis;

    private String location;
    private TodoType todoType;
    private List<String> voteOptions;

    private boolean hasResponded;
    private String userResponse;

    private String createdByUserPhone;
    private String emailAddress;

    public TaskPublicMinimalDTO(Task task, String userResponse) {
        this.title = task.getName();
        this.deadlineMillis = task.getDeadlineTime().toEpochMilli();
        this.createdByUserName = task.getCreatedByUser().getName();

        this.emailAddress = task.getCreatedByUser().getEmailAddress() == null ? "(Email not set)" :
                            task.getCreatedByUser().getEmailAddress();
        this.createdByUserPhone = task.getCreatedByUser().getPhoneNumber();

        switch (task.getTaskType()) {
            case MEETING:
                this.location = ((Meeting) task).getEventLocation();
                break;
            case VOTE:
                this.voteOptions = ((Vote) task).getVoteOptions();
                break;
            case TODO:
                this.todoType = ((Todo) task).getType();
                break;
        }

        this.hasResponded = !StringUtils.isEmpty(userResponse);
        this.userResponse = userResponse;
    }

}
