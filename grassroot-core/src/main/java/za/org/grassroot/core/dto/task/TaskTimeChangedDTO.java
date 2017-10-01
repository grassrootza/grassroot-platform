package za.org.grassroot.core.dto.task;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.InstantToMilliSerializer;

import java.time.Instant;

@Getter
public class TaskTimeChangedDTO {

    private String taskUid;
    private TaskType taskType;

    @JsonSerialize(using = InstantToMilliSerializer.class)
    private Instant lastTaskChange;

    public TaskTimeChangedDTO(String taskUid, Class<? extends Event> type, Instant lastTaskChange) {
        this.taskUid = taskUid;
        this.taskType = TaskType.ofClass(type);
        this.lastTaskChange = lastTaskChange;
    }

    public TaskTimeChangedDTO(String taskUid, String taskType, Instant lastTaskChange) {
        this.taskUid = taskUid;
        this.taskType = TaskType.valueOf(taskType);
        this.lastTaskChange = lastTaskChange;
    }
}
