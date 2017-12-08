package za.org.grassroot.core.dto.task;

import lombok.Getter;
import za.org.grassroot.core.enums.TaskType;

import java.time.Instant;

@Getter
public class TaskRefDTO {

    private final String taskUid;
    private final TaskType taskType;
    private final String title;
    private final Instant deadlineTime;

    public TaskRefDTO(String taskUid, TaskType taskType, String title, Instant deadlineTime) {
        this.taskUid = taskUid;
        this.taskType = taskType;
        this.title = title;
        this.deadlineTime = deadlineTime;
    }
}
