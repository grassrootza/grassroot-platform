package za.org.grassroot.core.dto.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModel;
import lombok.Getter;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Task;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;

@Getter
@ApiModel(value = "Minimal Task", description = "A stripped down set of information about a task")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskMinimalDTO {

    private final String taskUid;
    private final TaskType taskType;

    private final String parentUid;
    private final JpaEntityType parentType;

    private final String title;

    private final long createdDateTimeMillis;
    private final long deadlineDateTimeMillis;
    private final long lastChangeTimeServerMillis;

    public TaskMinimalDTO(Task task, Instant lastServerChangeTime) {
        this.taskUid = task.getUid();
        this.taskType = task.getTaskType();

        this.title = task.getName();
        this.parentType = task.getParent().getJpaEntityType();
        this.parentUid = task.getParent().getUid();

        this.createdDateTimeMillis = task.getCreatedDateTime().toEpochMilli();
        this.deadlineDateTimeMillis = task.getDeadlineTime().toEpochMilli();

        this.lastChangeTimeServerMillis = lastServerChangeTime.toEpochMilli();
    }
}
