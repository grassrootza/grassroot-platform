package za.org.grassroot.core.dto.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(value = "Full DTO for task (almost)", description = "Generic DTO for meetings, votes, and actions, a little " +
        "lighter and more streamlined than older TaskDTO")
public class TaskFullDTO {

    private final String taskUid;

    private final String title;
    private String description;
    private String location;

    private final String createdByUserName;
    private final boolean createdByThisUser;

    private final TaskType type;

    private final String parentUid;
    private final String parentName;
    private final String ancestorGroupName;

    private final Long createdTimeMillis;
    private final Long deadlineMillis;
    private final Long lastServerChangeMillis;

    private final boolean wholeGroupAssigned;
    private final boolean thisUserAssigned;

    private final String userResponse;
    private final boolean hasResponded;
    private final boolean canEdit;

    private final TodoType todoType;

    @Setter private Map<String, Long> voteResults;

    public TaskFullDTO(Task task, User user, Instant lastChangedTime, String userResponse) {
        this.taskUid = task.getUid();
        this.title = task.getName();
        this.description = task.getDescription();
        this.location = task.getTaskType().equals(TaskType.MEETING) ? ((Meeting) task).getEventLocation() : null;

        this.createdByUserName = task.getCreatedByUser().getName();
        this.createdByThisUser = task.getCreatedByUser().equals(user);
        this.ancestorGroupName = task.getAncestorGroup().getName();

        this.type = task.getTaskType();

        this.parentUid = task.getParent().getUid();
        this.parentName = task.getParent().getName();

        this.createdTimeMillis = task.getCreatedDateTime().toEpochMilli();
        this.deadlineMillis = task.getDeadlineTime().toEpochMilli();
        this.lastServerChangeMillis = lastChangedTime == null || lastChangedTime.toEpochMilli() == 0 ?
                task.getCreatedDateTime().toEpochMilli() : lastChangedTime.toEpochMilli();

        this.wholeGroupAssigned = task.isAllGroupMembersAssigned();
        this.thisUserAssigned = wholeGroupAssigned || task.getAssignedMembers().contains(user);


        this.canEdit = createdByThisUser;
        this.userResponse = userResponse;
        this.hasResponded = !StringUtils.isEmpty(userResponse);

        if (task instanceof Todo) {
            Todo todo = (Todo) task;
            this.todoType = todo.getType();
        } else this.todoType = null;
    }

}
